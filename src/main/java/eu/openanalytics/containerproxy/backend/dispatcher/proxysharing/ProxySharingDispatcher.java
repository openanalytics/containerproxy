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

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.dispatcher.IProxyDispatcher;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxyStatus;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.event.PendingProxyEvent;
import eu.openanalytics.containerproxy.event.SeatClaimedEvent;
import eu.openanalytics.containerproxy.event.SeatReleasedEvent;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.LocalDateTime;

public class ProxySharingDispatcher implements IProxyDispatcher {

    private ProxySharingMicrometer proxySharingMicrometer = null;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final IDelegateProxyStore delegateProxyStore;
    private final ISeatStore seatStore;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StructuredLogger slogger = new StructuredLogger(logger);
    private final ProxySharingScaler proxySharingScaler;

    static {
        RuntimeValueKeyRegistry.addRuntimeValueKey(SeatIdRuntimeValue.inst);
    }

    public ProxySharingDispatcher(IDelegateProxyStore delegateProxyStore,
                                  ISeatStore seatStore,
                                  ApplicationEventPublisher applicationEventPublisher,
                                  ProxySharingScaler proxySharingScaler) {
        this.delegateProxyStore = delegateProxyStore;
        this.seatStore = seatStore;
        this.applicationEventPublisher = applicationEventPublisher;
        this.proxySharingScaler = proxySharingScaler;
    }

    public static boolean supportSpec(ProxySpec proxySpec) {
        return proxySpec.getSpecExtension(ProxySharingSpecExtension.class).minimumSeatsAvailable != null;
    }

    public Seat claimSeat(String claimingProxyId) {
        return seatStore.claimSeat(claimingProxyId).orElse(null);
    }

    @Override
    public Proxy startProxy(Authentication user, Proxy proxy, ProxySpec spec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ProxyFailedToStartException {
        proxyStartupLogBuilder.startingApplication();
        LocalDateTime startTime = LocalDateTime.now();
        Seat seat = claimSeat(proxy.getId());
        if (seat == null) {
            slogger.info(proxy,"Seat not immediately available");
            applicationEventPublisher.publishEvent(new PendingProxyEvent(spec.getId(), proxy.getId()));
            // no seat available, busy loop until one becomes available
            // TODO replace by native async code, taking into account HA and multi seat per container
            for (int i = 0; i < 600; i++) {
                seat = claimSeat(proxy.getId());
                if (seat != null) {
                    slogger.info(proxy,"Seat available attempt: " + i);
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (seat == null) {
                throw new IllegalStateException("Could not claim a seat");
            }
        }
        applicationEventPublisher.publishEvent(new SeatClaimedEvent(spec.getId(), proxy.getId()));
        LocalDateTime endTime = LocalDateTime.now();
        if (proxySharingMicrometer != null) {
            proxySharingMicrometer.registerSeatWaitTime(spec.getId(), Duration.between(startTime, endTime));
        }

        // TODO NPE
        Proxy delegateProxy = delegateProxyStore.getDelegateProxy(seat.getDelegateProxyId()).getProxy();

        Proxy.ProxyBuilder resultProxy = proxy.toBuilder();
        resultProxy.targetId(delegateProxy.getId());
        resultProxy.addTargets(delegateProxy.getTargets());
        String publicPath = proxy.getRuntimeObjectOrNull(PublicPathKey.inst);
        if (publicPath != null) {
            resultProxy.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, publicPath.replaceAll(proxy.getId(), delegateProxy.getId())), true);
        }
        resultProxy.addRuntimeValue(new RuntimeValue(SeatIdRuntimeValue.inst, seat.getId()), true);

        return resultProxy.build();
    }

    @Override
    public void stopProxy(Proxy proxy) throws ContainerProxyException {
        String seatId = proxy.getRuntimeObjectOrNull(SeatIdRuntimeValue.inst);
        if (seatId != null) {
            Seat seat = seatStore.getSeat(seatId);
            DelegateProxy delegateProxy = delegateProxyStore.getDelegateProxy(seat.getDelegateProxyId());
            if (delegateProxy == null) {
                logger.warn("ProxySharing: DelegateProxy {} not found during stop of DelegatingProx: {}", seat.getDelegateProxyId(), proxy.getId());
            } else if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.Available)) {
                seatStore.releaseSeat(seatId, true);
            } else if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.ToRemove)) {
                seatStore.releaseSeat(seatId, false);
            } else {
                logger.warn("ProxySharing: DelegateProxy {} has unexpected state during stop of DelegatingProxy: {}", seat.getDelegateProxyId(), proxy.getId());
                seatStore.releaseSeat(seatId, false);
            }
        }
        applicationEventPublisher.publishEvent(new SeatReleasedEvent(proxy.getSpecId(), proxy.getId()));
    }

    @Override
    public void pauseProxy(Proxy proxy) {
        throw new IllegalStateException("Not available"); // TODO
    }

    @Override
    public Proxy resumeProxy(Authentication user, Proxy proxy, ProxySpec proxySpec) throws ProxyFailedToStartException {
        throw new IllegalStateException("Not available"); // TODO
    }

    @Override
    public boolean supportsPause() {
        return false;
    }

    @Override
    public Proxy addRuntimeValuesBeforeSpel(Authentication user, ProxySpec spec, Proxy proxy) {
        return proxy; // TODO
    }

    public Long getNumUnclaimedSeats() {
        return seatStore.getNumUnclaimedSeats();
    }

    public Long getNumClaimedSeats() {
        return seatStore.getNumClaimedSeats();
    }

    public void setProxySharingMicrometer(ProxySharingMicrometer proxySharingMicrometer) {
        this.proxySharingMicrometer = proxySharingMicrometer;
    }

    public ProxySharingScaler getProxySharingScaler() {
        return proxySharingScaler;
    }

}
