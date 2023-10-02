/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.stat.impl;

import eu.openanalytics.containerproxy.event.AuthFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.event.UserLoginEvent;
import eu.openanalytics.containerproxy.event.UserLogoutEvent;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.session.ISessionService;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

public class Micrometer implements IStatCollector {

    private static final int CACHE_UPDATE_INTERVAL = 20 * 1000; // update cache every 20 seconds

    @Inject
    private MeterRegistry registry;

    @Inject
    private ProxyService proxyService;

    @Inject
    private ISessionService sessionService;

    private final Logger logger = LogManager.getLogger(getClass());

    // keeps track of the number of proxies per spec id
    private final ConcurrentHashMap<String, Integer> proxyCountCache = new ConcurrentHashMap<>();

    // need to store a reference to the proxyCounters as the Micrometer library only stores weak references
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<ProxyCounter> proxyCounters = new ArrayList<>();

    private Counter appStartFailedCounter;

    private Counter authFailedCounter;

    private Counter userLogins;

    private Counter userLogouts;

    @PostConstruct
    public void init() {
        userLogins = registry.counter("userLogins");
        userLogouts = registry.counter("userLogouts");
        appStartFailedCounter = registry.counter("startFailed");
        authFailedCounter = registry.counter("authFailed");
        registry.gauge("absolute_users_logged_in", Tags.empty(), sessionService, wrapHandleNull(ISessionService::getLoggedInUsersCount));
        registry.gauge("absolute_users_active", Tags.empty(), sessionService, wrapHandleNull(ISessionService::getActiveUsersCount));

        for (ProxySpec spec : proxyService.getProxySpecs(null, true)) {
            registry.counter("appStarts", "spec.id", spec.getId());
            registry.counter("appStops", "spec.id", spec.getId());
            ProxyCounter proxyCounter = new ProxyCounter(spec.getId());
            proxyCounters.add(proxyCounter);
            registry.gauge("absolute_apps_running", Tags.of("spec.id", spec.getId()), proxyCounter, wrapHandleNull(ProxyCounter::getProxyCount));
            registry.timer("startupTime", "spec.id", spec.getId());
            registry.timer("applicationStartupTime", "spec.id", spec.getId());
            for (ContainerSpec containerSpec : spec.getContainerSpecs()) {
                registry.timer("imagePullTime", "spec.id", spec.getId(), "container.idx", containerSpec.getIndex().toString());
                registry.timer("containerScheduleTime", "spec.id", spec.getId(), "container.idx", containerSpec.getIndex().toString());
                registry.timer("containerStartupTime", "spec.id", spec.getId(), "container.idx", containerSpec.getIndex().toString());
            }
            registry.timer("usageTime", "spec.id", spec.getId());
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateCachedProxyCount();
            }
        }, 0, CACHE_UPDATE_INTERVAL);
    }

    @EventListener
    public void onUserLogoutEvent(UserLogoutEvent event) {
        logger.debug("UserLogoutEvent [user: {},  expired: {}]", event.getUserId(),  event.getWasExpired());
        userLogouts.increment();
    }

    @EventListener
    public void onUserLoginEvent(UserLoginEvent event) {
        logger.debug("UserLoginEvent [user: {}]", event.getUserId());
        userLogins.increment();
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) {
        logger.debug("ProxyStartEvent [user: {}]", event.getUserId());
        registry.counter("appStarts", "spec.id", event.getSpecId()).increment();

        ProxyStartupLog startupLog = event.getProxyStartupLog();
        startupLog.getCreateProxy().getStepDuration().ifPresent((d) -> {
            registry.timer("startupTime", "spec.id", event.getSpecId()).record(d);
        });

        startupLog.getPullImage().forEach((idx, step) -> {
            step.getStepDuration().ifPresent((d) -> {
                registry.timer("imagePullTime", "spec.id", event.getSpecId(), "container.idx", idx.toString()).record(d);
            });
        });

        startupLog.getScheduleContainer().forEach((idx, step) -> {
            step.getStepDuration().ifPresent((d) -> {
                registry.timer("containerScheduleTime", "spec.id", event.getSpecId(), "container.idx", idx.toString()).record(d);
            });
        });

        startupLog.getStartContainer().forEach((idx, step) -> {
            step.getStepDuration().ifPresent((d) -> {
                registry.timer("containerStartupTime", "spec.id", event.getSpecId(), "container.idx", idx.toString()).record(d);
            });
        });

        startupLog.getStartApplication().getStepDuration().ifPresent((d) -> {
            registry.timer("applicationStartupTime", "spec.id", event.getSpecId()).record(d);
        });
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) {
        logger.debug("ProxyStopEvent [user: {}, usageTime: {}]", event.getUserId(), event.getUsageTime());
        registry.counter("appStops", "spec.id", event.getSpecId()).increment();
        if (event.getUsageTime() != null) {
            registry.timer("usageTime", "spec.id", event.getSpecId()).record(event.getUsageTime());
        }
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) {
        logger.debug("ProxyStartFailedEvent [user: {}, specId: {}]", event.getUserId(), event.getSpecId());
        appStartFailedCounter.increment();
    }

    @EventListener
    public void onAuthFailedEvent(AuthFailedEvent event) {
        logger.debug("AuthFailedEvent [user: {}]", event.getUserId());
        authFailedCounter.increment();
    }

    /**
     * Updates the cache containing the number of proxies running for each spec id.
     * We only update this value every CACHE_UPDATE_INTERVAL because this is a relative heavy computation to do.
     * Therefore, we don't want that this calculation is performed every time the guage is updated.
     * Especially since this could be called using an HTTP request.
     */
    private void updateCachedProxyCount() {
        Map<String, Integer> intermediate = new HashMap<>();
        // for all specs, reset to zero
        for (String specId : proxyCountCache.keySet()) {
            intermediate.put(specId, 0);
        }
        // count number of running apps
        for (Proxy proxy : proxyService.getProxies(p -> p.getStatus() == ProxyStatus.Up, true)) {
            intermediate.put(proxy.getSpecId(), intermediate.getOrDefault(proxy.getSpecId(), 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : intermediate.entrySet()) {
            createMetersForExistingProxySpec(entry.getKey());
            proxyCountCache.put(entry.getKey(), entry.getValue());
            logger.debug(String.format("Running proxies count for spec %s: %s ", entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Creates the meters for a given proxy spec in case this proxy is not part of our application.yml
     * (e.g. app recovery, operator)
     */
    private void createMetersForExistingProxySpec(String specId) {
        try {
            registry.get("absolute_apps_running").tag("spec.id", specId).gauge();
        } catch (MeterNotFoundException e) {
            registry.counter("appStops", "spec.id", specId);
            ProxyCounter proxyCounter = new ProxyCounter(specId);
            proxyCounters.add(proxyCounter);
            registry.gauge("absolute_apps_running", Tags.of("spec.id", specId), proxyCounter, wrapHandleNull(ProxyCounter::getProxyCount));
            registry.timer("usageTime", "spec.id", specId);
        }
    }

    private class ProxyCounter {

        private final String specId;

        public ProxyCounter(String specId) {
            this.specId = specId;
        }

        public Integer getProxyCount() {
            return proxyCountCache.getOrDefault(specId, null);
        }

    }

    /**
     * Wraps a function that returns an Integer into a function that returns a double.
     * When the provided Integer is null, the resulting function returns Double.NaN.
     *
     * We need this function because Micrometer cannot handle null values for Gauges.
     */
    private static <T> ToDoubleFunction<T> wrapHandleNull(ToIntegerFunction<T> producer) {
        return (state) -> {
            Integer res = producer.applyAsDouble(state);
            if (res == null) {
                return Double.NaN;
            }
            return res;
        };
    }

    @FunctionalInterface
    private interface ToIntegerFunction<T> {
        Integer applyAsDouble(T var1);
    }

}
