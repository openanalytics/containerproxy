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

import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.event.PendingProxyEvent;
import eu.openanalytics.containerproxy.event.SeatClaimedEvent;
import eu.openanalytics.containerproxy.event.SeatReleasedEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;

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
    private final List<String> pendingDelegateProxies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pendingDelegatingProxies = Collections.synchronizedList(new ArrayList<>());
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ProxySpec proxySpec;
    private final RuntimeValueService runtimeValueService;
    private final SpecExpressionResolver expressionResolver;
    private final Thread eventProcessor;

    private static String publicPathPrefix = "/api/route/";
    private final Lock unclaimedSeatsLock;

    public static void setPublicPathPrefix(String publicPathPrefix) {
        ProxySharingScaler.publicPathPrefix = publicPathPrefix;
    }

    // TODO add cleanup of proxies that never became ready

    public ProxySharingScaler(ILeaderService leaderService, ISeatStore seatStore, ProxySpec proxySpec, IDelegateProxyStore delegateProxyStore, IContainerBackend containerBackend, SpecExpressionResolver expressionResolver,
                              RuntimeValueService runtimeValueService, IProxyTestStrategy testStrategy, Lock unclaimedSeatsLock) {
        this.leaderService = leaderService;
        this.proxySpec = proxySpec;
        this.minimumSeatsAvailable = proxySpec.getSpecExtension(ProxySharingSpecExtension.class).minimumSeatsAvailable;
        this.maximumSeatsAvailable = proxySpec.getSpecExtension(ProxySharingSpecExtension.class).maximumSeatsAvailable;
        this.unclaimedSeatsLock = unclaimedSeatsLock;

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
                    // TODO
                    break;
                } catch (Exception ex) {
//                            logger.error("Error", ex);
                    ex.printStackTrace();
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
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (leaderService.isLeader()) {
                    channel.add(Event.RECONCILE);
                }
            }
        }, 0, 10_000);
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
        long num = seatStore.getNumUnclaimedSeats() + pendingDelegateProxies.size() - pendingDelegatingProxies.size();
        logger.info(String.format("%s : Unclaimed: %s + pendingDelegate: %s - pendingDelegating: %s ? minimum: %s %n", proxySpec.getId(), seatStore.getNumUnclaimedSeats(), pendingDelegateProxies.size(), pendingDelegatingProxies.size(), minimumSeatsAvailable));
        if (num == minimumSeatsAvailable) {
            logger.info("No scaling required " + proxySpec.getId());
        } else if (num < minimumSeatsAvailable) {
            long numToScaleUp = minimumSeatsAvailable - num;
            logger.info("Scale up required, needing " + numToScaleUp + " " + proxySpec.getId());
            for (int i = 0; i < numToScaleUp; i++) {
                String id = UUID.randomUUID().toString();
                pendingDelegateProxies.add(id);
                executor.submit(createDelegateProxyJob(id));
            }
        } else if (num > maximumSeatsAvailable) {
            long numToScaleDown = num - maximumSeatsAvailable;
            logger.info("Scale down required, removing " + numToScaleDown + " " + proxySpec.getId());
            scaleDown(numToScaleDown);
        }
    }

    private Runnable createDelegateProxyJob(String id) {
        return () -> {
            try {
                Proxy.ProxyBuilder proxyBuilder = Proxy.builder();
                logger.info("Creating DelegateProxy " + id);

                proxyBuilder.id(id);
                proxyBuilder.targetId(id);
                proxyBuilder.status(ProxyStatus.New);
                proxyBuilder.specId(proxySpec.getId());
                proxyBuilder.createdTimestamp(System.currentTimeMillis());
                // TODO add minimal set of runtimevalues
                proxyBuilder.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, publicPathPrefix + id), false);

                // create container objects
                Proxy proxy = proxyBuilder.build();
                delegateProxyStore.addDelegateProxy(new DelegateProxy(proxy, Set.of()));

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
                logger.info("Starting DelegateProxy " + id);
                proxy = containerBackend.startProxy(null, proxy, resolvedSpec, null);
                delegateProxyStore.updateDelegateProxy(new DelegateProxy(proxy, Set.of()));

                if (!testStrategy.testProxy(proxy)) {
                    logger.info("Failed to start delegate proxy (did not come online)" + id); // TODO
                }

                proxy = proxy.toBuilder()
                    .startupTimestamp(System.currentTimeMillis())
                    .status(ProxyStatus.Up)
                    .build();

                Seat seat = new Seat(proxy.getId());
                delegateProxyStore.updateDelegateProxy(new DelegateProxy(proxy, Set.of(seat.getId())));
                seatStore.addSeat(seat);
                logger.info("Started DelegateProxy " + id);
            } catch (ProxyFailedToStartException ex) {
                logger.error("Failed to start delegate proxy", ex);
            } finally {
                pendingDelegateProxies.remove(id);
                channel.add(ProxySharingScaler.Event.RECONCILE); // re-trigger reconcile in-case startup failed
            }
        };
    }

    private void scaleDown(long numToScaleDown) {
        if (unclaimedSeatsLock.tryLock()) {
            logger.info("Got lock to scale down");
            // first find proxies of which all seats are unclaimed and already remove these from the unclaimed store
            List<DelegateProxy> delegateProxiesToRemove = new ArrayList<>();
            for (DelegateProxy delegateProxy : delegateProxyStore.getAllDelegateProxies()) {
                if (seatStore.areSeatsUnclaimed(delegateProxy.getSeatIds())) {
                    delegateProxiesToRemove.add(delegateProxy);
                    seatStore.removeSeats(delegateProxy.getSeatIds());
                    if (delegateProxiesToRemove.size() == numToScaleDown) {
                        break;
                    }
                }
            }
            // seats are removed -> unlock
            unclaimedSeatsLock.unlock();
            // only now remove the proxies (this takes the most time)
            for (DelegateProxy delegateProxy : delegateProxiesToRemove) {
                containerBackend.stopProxy(delegateProxy.getProxy());
                delegateProxyStore.removeDelegateProxy(delegateProxy);
            }
            logger.info(String.format("Found %s proxies to remove", delegateProxiesToRemove.size()));
        } else {
            logger.info("Could not get lock to scale down");
        }
    }

    public Long getNumCreatingSeats() {
        return (long) pendingDelegateProxies.size();
    }

    private enum Event {
        RECONCILE
    }

}
