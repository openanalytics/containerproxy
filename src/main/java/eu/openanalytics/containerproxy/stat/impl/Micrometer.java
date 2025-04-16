/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import eu.openanalytics.containerproxy.event.AuthFailedEvent;
import eu.openanalytics.containerproxy.event.NewProxyEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.event.UserLoginEvent;
import eu.openanalytics.containerproxy.event.UserLogoutEvent;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.ProxyStopReason;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerName;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.session.ISessionService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class Micrometer implements IStatCollector {

    private static final int CACHE_UPDATE_INTERVAL = 20 * 1000; // update cache every 20 seconds
    private final Logger logger = LoggerFactory.getLogger(getClass());
    // keeps track of the number of proxies per spec id
    private final ConcurrentHashMap<String, Integer> proxyCountCache = new ConcurrentHashMap<>();
    // need to store a reference to the proxyCounters as the Micrometer library only stores weak references
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<ProxyCounter> proxyCounters = new ArrayList<>();
    @Inject
    private MeterRegistry registry;
    @Inject
    private ProxyService proxyService;
    @Inject
    private ISessionService sessionService;
    @Inject
    private IProxySpecProvider specProvider;

    private Counter authFailedCounter;

    private Counter userLogins;

    private Counter userLogouts;

    private Cache<String, String> recentProxies;

    private static final Map<ProxyStatus, Integer> PROXY_STATUS_TO_INTEGER = Map.of(
        ProxyStatus.New, 1,
        ProxyStatus.Up, 10,
        ProxyStatus.Pausing, 20,
        ProxyStatus.Paused, 20,
        ProxyStatus.Resuming, 30,
        ProxyStatus.Stopping, 40,
        ProxyStatus.Stopped, 40
    );

    private static final Integer PROXY_STATUS_CRASHED_TO_INTEGER = 50;
    private static final Integer PROXY_STATUS_FAILED_TO_START_TO_INTEGER = 100;

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

    @PostConstruct
    public void init() {
        recentProxies = Caffeine.newBuilder()
            .scheduler(Scheduler.systemScheduler())
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build();
        userLogins = registry.counter("userLogins");
        userLogouts = registry.counter("userLogouts");
        authFailedCounter = registry.counter("authFailed");
        registry.gauge("absolute_users_logged_in", Tags.empty(), sessionService, wrapHandleNull(ISessionService::getLoggedInUsersCount));
        registry.gauge("absolute_users_active", Tags.empty(), sessionService, wrapHandleNull(ISessionService::getActiveUsersCount));

        for (ProxySpec spec : specProvider.getSpecs()) {
            registry.counter("appStarts", "spec.id", spec.getId());
            registry.counter("appStops", "spec.id", spec.getId());
            registry.counter("appCrashes", "spec.id", spec.getId());
            registry.counter("startFailed", "spec.id", spec.getId());
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
                updateAppInfo();
            }
        }, 0, CACHE_UPDATE_INTERVAL);
    }

    @EventListener
    public void onUserLogoutEvent(UserLogoutEvent event) {
        logger.debug("UserLogoutEvent [user: {},  expired: {}]", event.getUserId(), event.getWasExpired());
        userLogouts.increment();
    }

    @EventListener
    public void onUserLoginEvent(UserLoginEvent event) {
        logger.debug("UserLoginEvent [user: {}]", event.getUserId());
        userLogins.increment();
    }

    @EventListener
    public void onNewProxyEvent(NewProxyEvent event) {
        try {
            // must run on each instance (gauge is registered on every instance)
            if (event.getUserId() != null && event.getBackendContainerName() != null) {
                registry.gauge("appInfo",
                    Tags.of(
                        "spec.id", event.getSpecId(),
                        "user.id", event.getUserId(),
                        "proxy.instance", event.getInstance(),
                        "proxy.id", event.getProxyId(),
                        "proxy.created.timestamp", event.getCreatedTimestamp().toString(),
                        "resource.id", event.getBackendContainerName().getName(),
                        "proxy.namespace", event.getBackendContainerName().getNamespace()),
                    PROXY_STATUS_TO_INTEGER.get(ProxyStatus.New)
                );
                recentProxies.put(event.getProxyId(), event.getProxyId());
            }
            logger.debug("NewProxyEvent [user: {}]", event.getUserId());
        } catch (Exception e) {
            logger.error("Error in onNewProxyEvent", e);
        }
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) {
        try {
            // must run on each instance (gauge is registered on every instance)
            if (event.getBackendContainerName() != null) {
                removeExistingAppInfo(event.getProxyId());
                registry.gauge("appInfo",
                    Tags.of(
                        "spec.id", event.getSpecId(),
                        "user.id", event.getUserId(),
                        "proxy.instance", event.getInstance(),
                        "proxy.id", event.getProxyId(),
                        "proxy.created.timestamp", event.getCreatedTimestamp().toString(),
                        "resource.id", event.getBackendContainerName().getName(),
                        "proxy.namespace", event.getBackendContainerName().getNamespace()),
                    PROXY_STATUS_TO_INTEGER.get(ProxyStatus.Up)
                );
                recentProxies.put(event.getProxyId(), event.getProxyId());
            }
            if (!event.isLocalEvent()) {
                return;
            }
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
        } catch (Exception e) {
            logger.error("Error in onProxyStartEvent", e);
        }
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) {
        try {
            recentProxies.put(event.getProxyId(), event.getProxyId());
            // must run on each instance (gauge is registered on every instance)
            Integer value = PROXY_STATUS_TO_INTEGER.get(ProxyStatus.Stopped);
            if (event.getProxyStopReason().equals(ProxyStopReason.Crashed)) {
                value = PROXY_STATUS_CRASHED_TO_INTEGER;
            }
            String resourceId;
            String namespace;
            Gauge gauge = removeExistingAppInfo(event.getProxyId());
            if (event.getBackendContainerName() != null) {
                resourceId = event.getBackendContainerName().getName();
                namespace = event.getBackendContainerName().getNamespace();
            } else if (gauge != null) {
                resourceId = gauge.getId().getTag("resource.id");
                namespace = gauge.getId().getTag("proxy.namespace");
            } else {
                resourceId = "NA";
                namespace = "NA";
            }
            registry.gauge("appInfo",
                Tags.of(
                    "spec.id", event.getSpecId(),
                    "user.id", event.getUserId(),
                    "proxy.instance", event.getInstance(),
                    "proxy.id", event.getProxyId(),
                    "proxy.created.timestamp", event.getCreatedTimestamp().toString(),
                    "resource.id", resourceId,
                    "proxy.namespace", namespace),
                value
            );
            if (!event.isLocalEvent()) {
                return;
            }
            logger.debug("ProxyStopEvent [user: {}, usageTime: {}]", event.getUserId(), event.getUsageTime());
            registry.counter("appStops", "spec.id", event.getSpecId()).increment();
            if (event.getUsageTime() != null) {
                registry.timer("usageTime", "spec.id", event.getSpecId()).record(event.getUsageTime());
            }
            if (event.getProxyStopReason() == ProxyStopReason.Crashed) {
                registry.counter("appCrashes", "spec.id", event.getSpecId()).increment();
            }
        } catch (Exception e) {
            logger.error("Error in onProxyStopEvent", e);
        }
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) {
        try {
            recentProxies.put(event.getProxyId(), event.getProxyId());
            // must run on each instance (gauge is registered on every instance)
            String resourceId;
            String namespace;
            Gauge gauge = removeExistingAppInfo(event.getProxyId());
            if (event.getBackendContainerName() != null) {
                resourceId = event.getBackendContainerName().getName();
                namespace = event.getBackendContainerName().getNamespace();
            } else if (gauge != null) {
                resourceId = gauge.getId().getTag("resource.id");
                namespace = gauge.getId().getTag("proxy.namespace");
            } else {
                resourceId = "NA";
                namespace = "NA";
            }
            registry.gauge("appInfo",
                Tags.of(
                    "spec.id", event.getSpecId(),
                    "user.id", event.getUserId(),
                    "proxy.instance", event.getInstance(),
                    "proxy.id", event.getProxyId(),
                    "proxy.created.timestamp", event.getCreatedTimestamp().toString(),
                    "resource.id", resourceId,
                    "proxy.namespace", namespace
                ),
                PROXY_STATUS_FAILED_TO_START_TO_INTEGER
            );
            if (!event.isLocalEvent()) {
                return;
            }
            logger.debug("ProxyStartFailedEvent [user: {}, specId: {}]", event.getUserId(), event.getSpecId());
            registry.counter("startFailed", "spec.id", event.getSpecId()).increment();
        } catch (Exception e) {
            logger.error("Error in onProxyStartFailedEvent", e);
        }
    }

    @EventListener
    public void onAuthFailedEvent(AuthFailedEvent event) {
        logger.debug("AuthFailedEvent [user: {}]", event.getUserId());
        authFailedCounter.increment();
    }

    /**
     * Updates the cache containing the number of proxies running for each spec id.
     * We only update this value every CACHE_UPDATE_INTERVAL because this is a relative heavy computation to do.
     * Therefore, we don't want that this calculation is performed every time the gauge is updated.
     * Especially since this could be called using an HTTP request.
     */
    private void updateCachedProxyCount() {
        Map<String, Integer> intermediate = new HashMap<>();
        // for all specs, reset to zero
        for (String specId : proxyCountCache.keySet()) {
            intermediate.put(specId, 0);
        }
        // count number of running apps
        for (Proxy proxy : proxyService.getAllUpProxies()) {
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

    @FunctionalInterface
    private interface ToIntegerFunction<T> {
        Integer applyAsDouble(T var1);
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
     * Registers the appInfo gauge for every running app and removes any registrations for apps that have been stopped.
     * All gauges must be registered on every replica. If the gauge was only registered on the instance the app was started,
     * it would be removed from prometheus when that instance stopped, although the app could still be running.
     *
     * The periodic sync keeps the registry in sync (e.g. on startup).
     * It also ensure the appInfo gauge is removed after the proxy has stopped. We can't do this in the onProxyStopEvent function,
     * since otherwise the Gauge will not be updated to the Stopped state.
     */
    private void updateAppInfo() {
        try {
            Map<String, Gauge> existingGauges = getAppInfoGauges();
            for (Proxy proxy : proxyService.getAllProxies()) {
                Gauge existingGauge = existingGauges.remove(proxy.getId());
                if (existingGauge != null && (existingGauge.value() == PROXY_STATUS_TO_INTEGER.get(proxy.getStatus())
                    || existingGauge.value() == PROXY_STATUS_CRASHED_TO_INTEGER
                    || existingGauge.value() == PROXY_STATUS_FAILED_TO_START_TO_INTEGER)) {
                    // gauge already exists and value is correct
                    continue;
                }
                if (existingGauge != null) {
                    registry.remove(existingGauge);
                }

                BackendContainerName backendContainerName = getBackendContainerName(proxy);
                if (backendContainerName == null) {
                    // container not fully ready, will be registered later
                    continue;
                }
                registry.gauge("appInfo",
                    Tags.of(
                        "spec.id", proxy.getSpecId(),
                        "user.id", proxy.getUserId(),
                        "proxy.instance", proxy.getRuntimeValueOrDefault("SHINYPROXY_APP_INSTANCE", ""),
                        "proxy.id", proxy.getId(),
                        "proxy.created.timestamp", Long.toString(proxy.getCreatedTimestamp()),
                        "resource.id", backendContainerName.getName(),
                        "proxy.namespace", backendContainerName.getNamespace()),
                    PROXY_STATUS_TO_INTEGER.get(proxy.getStatus())
                );
            }
            for (Gauge gauge : existingGauges.values()) {
                String proxyId = gauge.getId().getTag("proxy.id");
                if (proxyId != null && recentProxies.getIfPresent(proxyId) != null) {
                    // this proxy was recently stopped, we should not yet remove the gauge
                    // so that the metrics systems knows the app was stopped
                    if (gauge.value() != PROXY_STATUS_TO_INTEGER.get(ProxyStatus.Stopped)
                        && gauge.value() != PROXY_STATUS_CRASHED_TO_INTEGER
                        && gauge.value() != PROXY_STATUS_FAILED_TO_START_TO_INTEGER) {
                        // gauge not yet updated -> set is as stopped
                        registry.remove(gauge);
                        registry.gauge("appInfo",
                            Tags.of(
                                "spec.id", gauge.getId().getTag("spec.id"),
                                "user.id", gauge.getId().getTag("user.id"),
                                "proxy.instance", gauge.getId().getTag("proxy.instance"),
                                "proxy.id", gauge.getId().getTag("proxy.id"),
                                "proxy.created.timestamp", gauge.getId().getTag("proxy.created.timestamp"),
                                "resource.id", gauge.getId().getTag("resource.id"),
                                "proxy.namespace", gauge.getId().getTag("proxy.namespace")),
                            PROXY_STATUS_TO_INTEGER.get(ProxyStatus.Stopped)
                        );
                    }
                    continue;
                }
                // the proxy of this gauge no longer exists -> remove the gauge
                registry.remove(gauge);
            }
        } catch (Exception e) {
            logger.error("Error in updateAppInfo", e);
        }
    }

    private Map<String, Gauge> getAppInfoGauges() {
        try {
            return new HashMap<>(registry.get("appInfo").gauges().stream()
                .collect(Collectors.toMap(g -> g.getId().getTag("proxy.id"), g -> g)));
        } catch (MeterNotFoundException ignored) {
            return new HashMap<>();
        }
    }

    private Gauge removeExistingAppInfo(String proxyId) {
        try {
            Gauge gauge = registry.get("appInfo").tag("proxy.id", proxyId).gauge();
            registry.remove(gauge);
            return gauge;
        } catch (MeterNotFoundException ignored) {

        }
        return null;
    }

    private BackendContainerName getBackendContainerName(Proxy proxy) {
        if (!proxy.getContainers().isEmpty()) {
            return proxy.getContainers().getFirst().getRuntimeObjectOrNull(BackendContainerNameKey.inst);
        }
        return null;
    }

}
