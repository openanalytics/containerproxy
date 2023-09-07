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

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxyStatus;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.redis.RedisSeatStore;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.event.PendingProxyEvent;
import eu.openanalytics.containerproxy.event.SeatClaimedEvent;
import eu.openanalytics.containerproxy.event.SeatReleasedEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.RuntimeValueService;
import eu.openanalytics.containerproxy.service.leader.ILeaderService;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class ProxySharingScaler {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final IContainerBackend containerBackend;
    private final IDelegateProxyStore delegateProxyStore;
    private final ILeaderService leaderService;
    private final IProxyTestStrategy testStrategy;
    private final ISeatStore seatStore;
    private final Integer maximumSeatsAvailable;
    private final Integer minimumSeatsAvailable;
    private final LinkedBlockingQueue<Event> channel = new LinkedBlockingQueue<>();
    private final List<String> pendingDelegatingProxies = Collections.synchronizedList(new ArrayList<>());
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ProxySpec proxySpec;
    private final RuntimeValueService runtimeValueService;
    private final SpecExpressionResolver expressionResolver;
    private final Thread eventProcessor;

    private static String publicPathPrefix = "/api/route/";

    public static void setPublicPathPrefix(String publicPathPrefix) {
        ProxySharingScaler.publicPathPrefix = publicPathPrefix;
    }

    // TODO add cleanup of proxies that never became ready

    public ProxySharingScaler(ILeaderService leaderService, ISeatStore seatStore, ProxySpec proxySpec, IDelegateProxyStore delegateProxyStore, IContainerBackend containerBackend, SpecExpressionResolver expressionResolver,
                              RuntimeValueService runtimeValueService, IProxyTestStrategy testStrategy) {
        this.leaderService = leaderService;
        this.proxySpec = proxySpec;
        this.minimumSeatsAvailable = proxySpec.getSpecExtension(ProxySharingSpecExtension.class).minimumSeatsAvailable;
        this.maximumSeatsAvailable = proxySpec.getSpecExtension(ProxySharingSpecExtension.class).maximumSeatsAvailable;

        eventProcessor = new Thread(() -> {
            while (true) {
                try {
                    Event event = channel.take();
                    if (!leaderService.isLeader()) {
                        // not the leader -> ignore events send to this channel
                        return;
                    }

                    if (event == Event.RECONCILE) {
                        reconcile();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ex) {
                    logger.error("Error while processing event for spec: " + proxySpec.getId(), ex);
                }
            }
        });
        this.seatStore = seatStore;
        this.delegateProxyStore = delegateProxyStore;
        this.containerBackend = containerBackend;
        this.expressionResolver = expressionResolver;
        this.runtimeValueService = runtimeValueService;
        this.testStrategy = testStrategy;
        eventProcessor.start();
    }

    @Scheduled(fixedDelay = 10_000)
    public void forceReconcile() {
        if (leaderService.isLeader()) {
            channel.add(Event.RECONCILE);
        }
    }

    @EventListener
    public void onPendingProxyEvent(PendingProxyEvent pendingProxyEvent) {
        if (!Objects.equals(pendingProxyEvent.getSpecId(), proxySpec.getId())) {
            // only handle events for this spec
            return;
        }
        pendingDelegatingProxies.add(pendingProxyEvent.getProxyId());
        channel.add(Event.RECONCILE);
    }

    @EventListener
    public void onSeatClaimedEvent(SeatClaimedEvent seatClaimedEvent) {
        if (!Objects.equals(seatClaimedEvent.getSpecId(), proxySpec.getId())) {
            // only handle events for this spec
            return;
        }
        channel.add(Event.RECONCILE);
        // if the seat was claimed by a pending proxy we need to remove it from the pendingDelegatingProxies
        pendingDelegatingProxies.remove(seatClaimedEvent.getClaimingProxyId());
    }

    @EventListener
    public void onSeatReleasedEvent(SeatReleasedEvent seatReleasedEvent) {
        if (!Objects.equals(seatReleasedEvent.getSpecId(), proxySpec.getId())) {
            // only handle events for this spec
            return;
        }
        // remove the claimingProxyId from pending in case the proxy stopped (or failed) while starting up
        pendingDelegatingProxies.remove(seatReleasedEvent.getClaimingProxyId());
    }

    private void reconcile() {
        long numPendingSeats = getNumPendingSeats();
        long num = seatStore.getNumUnclaimedSeats() + numPendingSeats - pendingDelegatingProxies.size();
        logger.debug(String.format("ProxySharing: [spec: %s] : Unclaimed: %s + pendingDelegate: %s - pendingDelegating: %s == %s -> minimum: %s",
            proxySpec.getId(), seatStore.getNumUnclaimedSeats(), numPendingSeats, pendingDelegatingProxies.size(), num, minimumSeatsAvailable));
        if (num < minimumSeatsAvailable) {
            long numToScaleUp = minimumSeatsAvailable - num;
            scaleUp(numToScaleUp);
        } else if (num > maximumSeatsAvailable) {
            long numToScaleDown = num - maximumSeatsAvailable;
            log(String.format("Scale down required, trying to remove %s DelegateProxies", numToScaleDown));
            scaleDown(numToScaleDown);
        } else {
            log("No scaling required");
        }
    }

    private void scaleUp(long numToScaleUp) {
        log(String.format("Scale up required, trying to create %s DelegateProxies", numToScaleUp));
        for (int i = 0; i < numToScaleUp; i++) {
            // store pending proxy here, so that this pending proxy is taking into account in the scaleUp function
            // in order to not overshoot
            String id = UUID.randomUUID().toString();
            log(String.format("Creating DelegateProxy %s", id));
            Proxy.ProxyBuilder proxyBuilder = Proxy.builder();
            proxyBuilder.id(id);
            Proxy proxy = proxyBuilder.build();
            delegateProxyStore.addDelegateProxy(new DelegateProxy(proxy, Set.of(), DelegateProxyStatus.Pending));
            executor.submit(createDelegateProxyJob(proxy));
        }

    }

    private Runnable createDelegateProxyJob(Proxy originalProxy) {
        String id = originalProxy.getId();
        return () -> {
            try {
                Proxy.ProxyBuilder proxyBuilder = originalProxy.toBuilder();
                log(String.format("Creating DelegateProxy %s", id));

                proxyBuilder.targetId(id);
                proxyBuilder.status(ProxyStatus.New);
                proxyBuilder.specId(proxySpec.getId());
                proxyBuilder.createdTimestamp(System.currentTimeMillis());
                // TODO add minimal set of runtimevalues
                proxyBuilder.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, publicPathPrefix + id), false);

                // create container objects
                Proxy proxy = proxyBuilder.build();
                delegateProxyStore.addDelegateProxy(new DelegateProxy(proxy, Set.of(), DelegateProxyStatus.Pending));

                SpecExpressionContext context = SpecExpressionContext.create(proxy, proxySpec);
                ProxySpec resolvedSpec = proxySpec.firstResolve(expressionResolver, context);
                context = context.copy(resolvedSpec, proxy);
                resolvedSpec = resolvedSpec.finalResolve(expressionResolver, context);

                for (ContainerSpec containerSpec : resolvedSpec.getContainerSpecs()) {
                    Container.ContainerBuilder containerBuilder = Container.builder();
                    containerBuilder.index(containerSpec.getIndex());
                    Container container = containerBuilder.build();
                    container = runtimeValueService.addRuntimeValuesAfterSpel(containerSpec, container);
                    proxyBuilder.addContainer(container);
                }
                proxy = proxyBuilder.build();
                // TODO use startupLog ?
                log(String.format("Starting DelegateProxy %s", id));
                proxy = containerBackend.startProxy(null, proxy, resolvedSpec, new ProxyStartupLog.ProxyStartupLogBuilder());
                delegateProxyStore.updateDelegateProxy(new DelegateProxy(proxy, Set.of(), DelegateProxyStatus.Pending));

                if (!testStrategy.testProxy(proxy)) {
                    logWarn(String.format("Failed to start DelegateProxy %s: Container did not respond in time", id));
                    delegateProxyStore.removeDelegateProxy(id);
                    return;
                }

                proxy = proxy.toBuilder()
                    .startupTimestamp(System.currentTimeMillis())
                    .status(ProxyStatus.Up)
                    .build();

                Seat seat = new Seat(proxy.getId());
                delegateProxyStore.updateDelegateProxy(new DelegateProxy(proxy, Set.of(seat.getId()), DelegateProxyStatus.Available));
                seatStore.addSeat(seat);
                log(String.format("Started DelegateProxy %s", id));
            } catch (Exception ex) {
                delegateProxyStore.removeDelegateProxy(id);
                logError(String.format("Failed to start DelegateProxy [id: %s]", id), ex);
                channel.add(ProxySharingScaler.Event.RECONCILE); // re-trigger reconcile in-case startup failed
            }
        };
    }

    private void scaleDown(long numToScaleDown) {
        List<DelegateProxy> delegateProxiesToRemove = new ArrayList<>();
        // first find proxies of which all seats are unclaimed and already remove these from the unclaimed store
        try {
            for (DelegateProxy delegateProxy : delegateProxyStore.getAllDelegateProxies()) {
                if (delegateProxy.getProxy().getStatus() == ProxyStatus.Up && seatStore.removeSeatsIfUnclaimed(delegateProxy.getSeatIds())) {
                    delegateProxiesToRemove.add(delegateProxy);
                    if (delegateProxiesToRemove.size() == numToScaleDown) {
                        break;
                    }
                }
            }
        } catch (RedisSeatStore.SeatClaimedDuringRemovalException ex) {
            logger.info("Stopping scale down because a seat was claimed");
        }
        if (delegateProxiesToRemove.isEmpty()) {
            logger.info("No proxy found to remove during scale-down.");
            return;
        }
        log(String.format("Stopping %s DelegateProxies", delegateProxiesToRemove.size()));
        // only now remove the proxies (this takes the most time)
        for (DelegateProxy delegateProxy : delegateProxiesToRemove) {
            try {
                containerBackend.stopProxy(delegateProxy.getProxy());
                delegateProxyStore.removeDelegateProxy(delegateProxy.getProxy().getId());
            } catch (Exception ex) {
                logger.warn("Exception while removing DelegateProxy", ex);
            }
        }
    }

    public Long getNumPendingSeats() {
        return delegateProxyStore.getAllDelegateProxies().stream().filter(it -> it.getDelegateProxyStatus().equals(DelegateProxyStatus.Pending)).count();
    }

    private enum Event {
        RECONCILE
    }

    private void log(String message) {
        logger.info(String.format("ProxySharing: [spec: %s] %s", proxySpec.getId(), message));
    }

    private void logWarn(String message) {
        logger.warn(String.format("ProxySharing: [spec: %s] %s", proxySpec.getId(), message));
    }

    private void logError(String message, Throwable t) {
        logger.error(String.format("ProxySharing: [spec: %s] %s", proxySpec.getId(), message), t);
    }

}
