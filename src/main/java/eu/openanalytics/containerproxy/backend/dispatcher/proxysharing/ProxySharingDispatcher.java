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
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.dispatcher.IProxyDispatcher;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.event.PendingProxyEvent;
import eu.openanalytics.containerproxy.event.SeatAvailableEvent;
import eu.openanalytics.containerproxy.event.SeatClaimedEvent;
import eu.openanalytics.containerproxy.event.SeatReleasedEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.ProxyStopReason;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.TargetIdKey;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import eu.openanalytics.containerproxy.util.MathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class ProxySharingDispatcher implements IProxyDispatcher {

    private static final String PROPERTY_SEAT_WAIT_TIME = "proxy.seat-wait-time";

    static {
        RuntimeValueKeyRegistry.addRuntimeValueKey(SeatIdKey.inst);
        RuntimeValueKeyRegistry.addRuntimeValueKey(DelegateProxyKey.inst);
    }

    private final ProxySpec proxySpec;
    private final IDelegateProxyStore delegateProxyStore;
    private final ISeatStore seatStore;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StructuredLogger slogger = new StructuredLogger(logger);
    private Cache<String, CompletableFuture<Void>> pendingDelegatingProxies;
    private Long seatWaitIterations;
    @Inject
    private ApplicationEventPublisher applicationEventPublisher;
    @Inject
    private IProxyStore proxyStore;
    @Inject
    private Environment environment;
    @Autowired(required = false)
    private ProxySharingMicrometer proxySharingMicrometer = null;

    public ProxySharingDispatcher(ProxySpec proxySpec, IDelegateProxyStore delegateProxyStore, ISeatStore seatStore) {
        this.proxySpec = proxySpec;
        this.delegateProxyStore = delegateProxyStore;
        this.seatStore = seatStore;
    }

    public static boolean supportSpec(ProxySpec proxySpec) {
        return proxySpec.getSpecExtension(ProxySharingSpecExtension.class).minimumSeatsAvailable != null;
    }

    @PostConstruct
    public void init() {
        long seatWaitTime = environment.getProperty(PROPERTY_SEAT_WAIT_TIME, Long.class, 300000L);
        if (seatWaitTime < 3000) {
            throw new IllegalStateException("Invalid configuration: proxy.seat-wait-time must be larger than 3000 (3 seconds).");
        }
        seatWaitIterations = MathUtil.divideAndCeil(seatWaitTime, 3000);
        pendingDelegatingProxies = Caffeine
            .newBuilder()
            // use iterations to compute the expire time, since this may be larger than the requested time
            .expireAfterWrite(seatWaitIterations * 3000L * 2, TimeUnit.MILLISECONDS)
            .build();
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
            slogger.info(proxy, "Seat not immediately available");
            CompletableFuture<Void> future = new CompletableFuture<>();
            pendingDelegatingProxies.put(proxy.getId(), future);

            // trigger scale-up in scaler (possibly on different replica)
            applicationEventPublisher.publishEvent(new PendingProxyEvent(proxySpec.getId(), proxy.getId()));

            // no seat available, wait until one becomes available
            for (int i = 0; i < seatWaitIterations; i++) {
                try {
                    future.get(3, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                } catch (CancellationException e) {
                    // proxy was stopped, do not claim a seat, just return existing object
                    return proxy;
                } catch (TimeoutException e) {
                    // timeout reached, try to claim anyway in case some event was missed
                }
                if (proxyWasStopped(proxy)) {
                    // proxy was stopped, do not claim a seat, just return existing object
                    return proxy;
                }
                seat = claimSeat(proxy.getId());
                if (seat != null) {
                    slogger.info(proxy, "Seat available attempt: " + i);
                    break;
                }
            }
            if (seat == null) {
                cancelPendingDelegateProxy(proxy.getId());
                throw new ProxyFailedToStartException("Could not claim a seat within the configured wait-time", null, proxy);
            }
        }
        info(proxy, seat, "Seat claimed");
        applicationEventPublisher.publishEvent(new SeatClaimedEvent(spec.getId(), proxy.getId()));
        LocalDateTime endTime = LocalDateTime.now();
        if (proxySharingMicrometer != null) {
            proxySharingMicrometer.registerSeatWaitTime(spec.getId(), Duration.between(startTime, endTime));
        }

        Proxy delegateProxy = delegateProxyStore.getDelegateProxy(seat.getDelegateProxyId()).getProxy();

        Proxy.ProxyBuilder resultProxy = proxy.toBuilder();
        resultProxy.targetId(delegateProxy.getId());
        resultProxy.addTargets(delegateProxy.getTargets());
        resultProxy.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, delegateProxy.getRuntimeValue(PublicPathKey.inst)), true);
        resultProxy.addRuntimeValue(new RuntimeValue(TargetIdKey.inst, delegateProxy.getId()), true);
        resultProxy.addRuntimeValue(new RuntimeValue(SeatIdKey.inst, seat.getId()), true);

        Container resultContainer = proxy.getContainer(0).toBuilder().id(UUID.randomUUID().toString()).build();
        resultProxy.updateContainer(resultContainer);

        return resultProxy.build();
    }

    @Override
    public void stopProxy(Proxy proxy, ProxyStopReason proxyStopReason) throws ContainerProxyException {
        String seatId = proxy.getRuntimeObjectOrNull(SeatIdKey.inst);
        if (seatId != null) {
            seatStore.releaseSeat(seatId);
            info(proxy, seatStore.getSeat(seatId), "Seat released");
            applicationEventPublisher.publishEvent(new SeatReleasedEvent(proxy.getSpecId(), seatId, proxy.getId(), proxyStopReason));
        }

        // if proxy is still starting, cancel the future
        cancelPendingDelegateProxy(proxy.getId());
    }

    @Override
    public void pauseProxy(Proxy proxy) {
        throw new IllegalStateException("ProxySharingDispatcher does not support pauseProxy.");
    }

    @Override
    public Proxy resumeProxy(Authentication user, Proxy proxy, ProxySpec proxySpec) throws ProxyFailedToStartException {
        throw new IllegalStateException("ProxySharingDispatcher does not support resumeProxy.");
    }

    @Override
    public boolean supportsPause() {
        return false;
    }

    @Override
    public Proxy addRuntimeValuesBeforeSpel(Authentication user, ProxySpec spec, Proxy proxy) {
        return proxy;
    }

    @Override
    public Proxy addRuntimeValuesAfterSpel(Authentication user, ProxySpec spec, Proxy proxy) {
        return proxy;
    }

    @Override
    public boolean isProxyHealthy(Proxy proxy) {
        return false; // TODO
    }

    @EventListener
    public void onSeatAvailableEvent(SeatAvailableEvent event) {
        if (!Objects.equals(event.getSpecId(), proxySpec.getId())) {
            // only handle events for this spec
            return;
        }
        slogger.info(null, String.format("Received SeatAvailableEvent: %s %s", event.getIntendedProxyId(), event.getSpecId()));
        CompletableFuture<Void> future = pendingDelegatingProxies.getIfPresent(event.getIntendedProxyId());
        if (future == null) {
            return;
        }
        pendingDelegatingProxies.invalidate(event.getIntendedProxyId());
        future.complete(null);
    }

    public ProxySpec getSpec() {
        return proxySpec;
    }

    private void info(Proxy proxy, Seat seat, String message) {
        logger.info("[{} {} {} {} {}] " + message, kv("user", proxy.getUserId()), kv("proxyId", proxy.getId()), kv("specId", proxy.getSpecId()), kv("delegateProxyId", seat.getDelegateProxyId()), kv("seatId", seat.getId()));
    }

    private boolean proxyWasStopped(Proxy startingProxy) {
        // fetch proxy from proxyStore in order to check if it was stopped
        Proxy proxy = proxyStore.getProxy(startingProxy.getId());
        return proxy == null || proxy.getStatus().equals(ProxyStatus.Stopped)
            || proxy.getStatus().equals(ProxyStatus.Stopping);
    }

    private void cancelPendingDelegateProxy(String proxyId) {
        if (proxyId == null) {
            return;
        }
        CompletableFuture<Void> future = pendingDelegatingProxies.getIfPresent(proxyId);
        if (future == null) {
            return;
        }
        pendingDelegatingProxies.invalidate(proxyId);
        future.cancel(true);
    }

}
