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

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerName;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
        for (ProxySharingDispatcher dispatcher : proxySharingDispatchers) {
            String specId = dispatcher.getSpec().getId();
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

    private void updateDelegateAppInfo() {
        Map<String, Gauge> existingGauges = getDelegateAppInfoGauges();
        for (ProxySharingScaler scaler : proxySharingScalers) {
            String specId = scaler.getSpec().getId();
            for (DelegateProxy delegateProxy : scaler.getAllDelegateProxies()) {
                Proxy proxy = delegateProxy.getProxy();
                if (existingGauges.remove(proxy.getId()) != null) {
                    // gauge already exists, no need to re-create
                    continue;
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
                    1
                );
            }
        }
        for (Gauge gauge : existingGauges.values()) {
            // the proxy of this gauge no longer exists -> remove the gauge
            registry.remove(gauge);
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
