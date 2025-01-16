/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxyStatus;
import eu.openanalytics.containerproxy.event.NewProxyEvent;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerName;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class ProxySharingMicrometer implements IStatCollector {

    private static final int CACHE_UPDATE_INTERVAL = 20 * 1000; // update cache every 20 seconds

    @Inject
    private MeterRegistry registry;

    @Autowired(required = false)
    private List<ProxySharingDispatcher> proxySharingDispatchers = new ArrayList<>();

    @Autowired(required = false)
    private List<ProxySharingScaler> proxySharingScalers = new ArrayList<>();

    private final List<String> specIds = new ArrayList<>();

    private static final Map<DelegateProxyStatus, Integer> PROXY_STATUS_TO_INTEGER = Map.of(
        DelegateProxyStatus.Pending, 1,
        DelegateProxyStatus.Available, 10,
        DelegateProxyStatus.ToRemove, 20
    );

    private Cache<String, String> recentProxies;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Wraps a function that returns an Integer into a function that returns a double.
     * When the provided Integer is null, the resulting function returns Double.NaN.
     *
     * We need this function because Micrometer cannot handle null values for Gauges.
     */
    private static <T> ToDoubleFunction<T> wrapHandleNull(ToLongFunction<T> producer) {
        return (state) -> {
            Long res = producer.applyAsDouble(state);
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
        for (ProxySharingDispatcher dispatcher : proxySharingDispatchers) {
            String specId = dispatcher.getSpec().getId();
            specIds.add(specId);
            registry.timer("seats_wait_time", "spec.id", specId);
        }
        for (ProxySharingScaler scaler : proxySharingScalers) {
            String specId = scaler.getSpec().getId();
            registry.gauge("seats_unclaimed", Tags.of("spec.id", specId), scaler, wrapHandleNull(ProxySharingScaler::getNumUnclaimedSeats));
            registry.gauge("seats_claimed", Tags.of("spec.id", specId), scaler, wrapHandleNull(ProxySharingScaler::getNumClaimedSeats));
            registry.gauge("seats_creating", Tags.of("spec.id", specId), scaler, wrapHandleNull(ProxySharingScaler::getNumPendingSeats));
        }
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateDelegateAppInfo();
            }
        }, 0, CACHE_UPDATE_INTERVAL);
    }

    public void registerSeatWaitTime(String specId, Duration time) {
        registry.timer("seats_wait_time", "spec.id", specId).record(time);
    }

    @EventListener
    public void onNewProxyEvent(NewProxyEvent event) {
        if (event.getUserId() != null || event.getBackendContainerName() == null) {
            return;
        }
        if (specIds.contains(event.getSpecId())) {
            recentProxies.put(event.getProxyId(), event.getProxyId());
            registry.gauge("delegate_app_info",
                Tags.of(
                    "spec.id", event.getSpecId(),
                    "proxy.id", event.getProxyId(),
                    "proxy.created.timestamp", Long.toString(event.getCreatedTimestamp()),
                    "resource.id", event.getBackendContainerName().getName(),
                    "proxy.namespace", event.getBackendContainerName().getNamespace()),
                PROXY_STATUS_TO_INTEGER.get(DelegateProxyStatus.Pending)
            );
        }
    }

    private void updateDelegateAppInfo() {
        try {
            Map<String, Gauge> existingGauges = getDelegateAppInfoGauges();
            for (ProxySharingScaler scaler : proxySharingScalers) {
                String specId = scaler.getSpec().getId();
                for (DelegateProxy delegateProxy : scaler.getAllDelegateProxies()) {
                    Proxy proxy = delegateProxy.getProxy();
                    recentProxies.put(proxy.getId(), proxy.getId());
                    Gauge existingGauge = existingGauges.remove(proxy.getId());
                    if (existingGauge != null && existingGauge.value() == PROXY_STATUS_TO_INTEGER.get(delegateProxy.getDelegateProxyStatus())) {
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

                    registry.gauge("delegate_app_info",
                        Tags.of(
                            "spec.id", specId,
                            "proxy.id", proxy.getId(),
                            "proxy.created.timestamp", Long.toString(proxy.getCreatedTimestamp()),
                            "resource.id", backendContainerName.getName(),
                            "proxy.namespace", backendContainerName.getNamespace()),
                        PROXY_STATUS_TO_INTEGER.get(delegateProxy.getDelegateProxyStatus())
                    );
                }
            }
            for (Gauge gauge : existingGauges.values()) {
                String proxyId = gauge.getId().getTag("proxy.id");
                if (proxyId != null && recentProxies.getIfPresent(proxyId) != null) {
                    // this DelegateProxy has been removed, mark it as ToRemove
                    // when the TTL of this proxy in recentProxies expires, the gauge will be removed
                    // this waiting period allows the metric system to pick up that the proxy is being removed
                    registry.remove(gauge);
                    registry.gauge("delegate_app_info",
                        Tags.of(
                            "spec.id", gauge.getId().getTag("spec.id"),
                            "proxy.id", gauge.getId().getTag("proxy.id"),
                            "proxy.created.timestamp", gauge.getId().getTag("proxy.created.timestamp"),
                            "resource.id", gauge.getId().getTag("resource.id"),
                            "proxy.namespace", gauge.getId().getTag("proxy.namespace")),
                        PROXY_STATUS_TO_INTEGER.get(DelegateProxyStatus.ToRemove)
                    );
                    continue;
                }

                // the proxy of this gauge no longer exists -> remove the gauge
                registry.remove(gauge);
            }
        } catch (Exception e) {
            logger.warn("Error while updating delegateAppInfo", e);
        }
    }

    private Map<String, Gauge> getDelegateAppInfoGauges() {
        try {
            return new HashMap<>(registry.get("delegate_app_info").gauges().stream()
                .collect(Collectors.toMap(g -> g.getId().getTag("proxy.id"), g -> g)));
        } catch (MeterNotFoundException ignored) {
            return new HashMap<>();
        }
    }

    private BackendContainerName getBackendContainerName(Proxy proxy) {
        if (!proxy.getContainers().isEmpty()) {
            return proxy.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst);
        }
        return null;
    }

    @FunctionalInterface
    private interface ToLongFunction<T> {
        Long applyAsDouble(T var1);
    }

}
