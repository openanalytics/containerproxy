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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing;

import eu.openanalytics.containerproxy.backend.dispatcher.ProxyDispatcherService;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.time.Duration;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class ProxySharingMicrometer implements IStatCollector {

    private final Logger logger = LogManager.getLogger(getClass());
    @Inject
    private MeterRegistry registry;
    @Inject
    private IProxySpecProvider proxySpecProvider;
    @Inject
    private ProxyDispatcherService proxyDispatcherService;

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
        for (ProxySpec proxySpec : proxySpecProvider.getSpecs()) {
            if (ProxySharingDispatcher.supportSpec(proxySpec)) {
                ProxySharingDispatcher dispatcher = (ProxySharingDispatcher) proxyDispatcherService.getDispatcher(proxySpec.getId());
                registry.gauge("seats_unclaimed", Tags.of("spec.id", proxySpec.getId()), dispatcher, wrapHandleNull(ProxySharingDispatcher::getNumUnclaimedSeats));
                registry.gauge("seats_claimed", Tags.of("spec.id", proxySpec.getId()), dispatcher, wrapHandleNull(ProxySharingDispatcher::getNumClaimedSeats));
                registry.gauge("seats_creating", Tags.of("spec.id", proxySpec.getId()), dispatcher, wrapHandleNull(ProxySharingDispatcher::getNumCreatingSeats));
                registry.timer("seats_wait_time", "spec.id", proxySpec.getId());
                dispatcher.setProxySharingMicrometer(this);
            }
        }
    }

    public void registerSeatWaitTime(String specId, Duration time) {
        registry.timer("seats_wait_time", "spec.id", specId).record(time);
    }

    @FunctionalInterface
    private interface ToLongFunction<T> {
        Long applyAsDouble(T var1);
    }

}
