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

import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxyStatus;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.redis.RedisSeatStore;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.event.PendingProxyEvent;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.event.SeatAvailableEvent;
import eu.openanalytics.containerproxy.event.SeatClaimedEvent;
import eu.openanalytics.containerproxy.event.SeatReleasedEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.ProxyStopReason;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CreatedTimestampKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxyIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxySpecIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RealmIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ISpecExtension;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.LogService;
import eu.openanalytics.containerproxy.service.RuntimeValueService;
import eu.openanalytics.containerproxy.service.leader.GlobalEventLoopService;
import eu.openanalytics.containerproxy.service.leader.ILeaderService;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.spec.expression.SpelField;
import eu.openanalytics.containerproxy.util.ExecutorServiceFactory;
import eu.openanalytics.containerproxy.util.MathUtil;
import eu.openanalytics.containerproxy.util.Sha1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class ProxySharingScaler {

    private static String publicPathPrefix = "/api/route/";
    private final ExecutorService executor = ExecutorServiceFactory.create("ProxySharingScaler");
    private final IDelegateProxyStore delegateProxyStore;
    private final ISeatStore seatStore;
    private final ProxySharingSpecExtension specExtension;
    private final List<String> pendingDelegatingProxies = Collections.synchronizedList(new ArrayList<>());
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ProxySpec proxySpec;
    private final String proxySpecHash;
    private ReconcileStatus lastReconcileStatus = ReconcileStatus.Stable;
    private Instant lastScaleUp = null;

    @Inject
    private IProxyTestStrategy testStrategy;
    @Inject
    private IContainerBackend containerBackend;
    @Inject
    private RuntimeValueService runtimeValueService;
    @Inject
    private SpecExpressionResolver expressionResolver;
    @Inject
    private IdentifierService identifierService;
    @Inject
    private LogService logService;
    @Inject
    private GlobalEventLoopService globalEventLoop;
    @Inject
    private ILeaderService leaderService;
    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    public ProxySharingScaler(ISeatStore seatStore, ProxySpec proxySpec, IDelegateProxyStore delegateProxyStore) {
        this.specExtension = proxySpec.getSpecExtension(ProxySharingSpecExtension.class);
        this.seatStore = seatStore;
        this.delegateProxyStore = delegateProxyStore;
        // remove httpHeaders from spec, since it's not used for DelegateProxies and may contain SpEL which cannot be resolved here
        this.proxySpec = proxySpec.toBuilder().httpHeaders(new SpelField.StringMap()).build();
        proxySpecHash = getProxySpecHash(proxySpec);
    }

    public static void setPublicPathPrefix(String publicPathPrefix) {
        ProxySharingScaler.publicPathPrefix = publicPathPrefix;
    }

    @Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
    public void scheduleCleanup() {
        globalEventLoop.schedule(this::cleanup);
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void scheduleReconcile() {
        globalEventLoop.schedule(this::reconcile);
    }

    @EventListener
    public void onPendingProxyEvent(PendingProxyEvent pendingProxyEvent) {
        if (!Objects.equals(pendingProxyEvent.getSpecId(), proxySpec.getId()) || !leaderService.isLeader()) {
            // only handle events for this spec
            return;
        }
        pendingDelegatingProxies.add(pendingProxyEvent.getProxyId());
        globalEventLoop.schedule(this::reconcile);
    }

    @EventListener
    public void onSeatClaimedEvent(SeatClaimedEvent seatClaimedEvent) {
        if (!Objects.equals(seatClaimedEvent.getSpecId(), proxySpec.getId()) || !leaderService.isLeader()) {
            // only handle events for this spec
            return;
        }
        globalEventLoop.schedule(this::reconcile);
        // if the seat was claimed by a pending proxy we need to remove it from the pendingDelegatingProxies
        pendingDelegatingProxies.remove(seatClaimedEvent.getClaimingProxyId());
    }

    @EventListener
    public void onSeatReleasedEvent(SeatReleasedEvent seatReleasedEvent) {
        if (!Objects.equals(seatReleasedEvent.getSpecId(), proxySpec.getId()) || !leaderService.isLeader()) {
            // only handle events for this spec
            return;
        }
        globalEventLoop.schedule(() -> processReleasedSeat(seatReleasedEvent));
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent proxyStopEvent) {
        if (!Objects.equals(proxyStopEvent.getSpecId(), proxySpec.getId()) || !leaderService.isLeader()) {
            // only handle events for this spec
            return;
        }
        // remove pending proxy if any
        // this can happen if the proxy was stopped (by a user) before a seat was claimed
        pendingDelegatingProxies.remove(proxyStopEvent.getProxyId());
    }

    @EventListener
    public void onProxyStartFailed(ProxyStartFailedEvent proxyStartFailedEvent) {
        if (!Objects.equals(proxyStartFailedEvent.getSpecId(), proxySpec.getId()) || !leaderService.isLeader()) {
            // only handle events for this spec
            return;
        }
        // remove pending proxy if any
        // this can happen if the proxy was unable to claim a seat within the waiting time
        pendingDelegatingProxies.remove(proxyStartFailedEvent.getProxyId());
    }

    /**
     * Processes the SeatReleasedEvent, should only process one event a a time (i.e. using the event loop),
     * since it modifies the Delegateproxy.
     *
     * @param seatReleasedEvent the event to process
     */
    private void processReleasedSeat(SeatReleasedEvent seatReleasedEvent) {
        // remove the claimingProxyId from pending in case the proxy stopped (or failed) while starting up
        pendingDelegatingProxies.remove(seatReleasedEvent.getClaimingProxyId());

        String seatId = seatReleasedEvent.getSeatId();
        if (seatId == null) {
            logWarn("ProxySharing: SeatId null during processing of SeatReleasedEvent");
            return;
        }
        Seat seat = seatStore.getSeat(seatId);
        if (seat == null) {
            logWarn(String.format("ProxySharing: Seat %s not found during processing of SeatReleasedEvent", seatId));
            return;
        }
        DelegateProxy delegateProxy = delegateProxyStore.getDelegateProxy(seat.getDelegateProxyId());
        if (delegateProxy == null) {
            logWarn(String.format("ProxySharing: DelegateProxy %s not found during processing of SeatReleasedEvent with seatId: %s", seat.getDelegateProxyId(), seatId));
            return;
        }

        if (seatReleasedEvent.getProxyStopReason() == ProxyStopReason.Crashed) {
            // proxy crashed -> mark delegateProxy as ToRemove
            // this delegateProxy will be (completely) removed by the cleanup function, not by scale-down
            log(delegateProxy, "DelegateProxy crashed, marking for removal");
            removeSeat(delegateProxy, seatId);
            markDelegateProxyForRemoval(delegateProxy.getProxy().getId());
            globalEventLoop.schedule(this::reconcile);
        } else if (!specExtension.allowContainerReUse) {
            // TODO allow only one seat if container is not allowed to be re-used
            // container cannot be re-used -> mark delegateProxy as ToRemove
            log(delegateProxy, "DelegateProxy cannot be re-used, marking for removal");
            removeSeat(delegateProxy, seatId);
            markDelegateProxyForRemoval(delegateProxy.getProxy().getId());
            globalEventLoop.schedule(this::reconcile);
        } else if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.Available)) {
            seatStore.addToUnclaimedSeats(seatId);
        } else if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.ToRemove)) {
            // seat no longer needed, remove it
            removeSeat(delegateProxy, seatId);
        }

    }

    private void markDelegateProxyForRemoval(String delegateProxyId) {
        // this delegateProxy will be (completely) removed by the cleanup function, not by scale-down
        DelegateProxy delegateProxy = delegateProxyStore.getDelegateProxy(delegateProxyId);
        Set<String> seatIds = delegateProxy.getSeatIds();
        DelegateProxy.DelegateProxyBuilder delegateProxyBuilder = delegateProxy.toBuilder()
            .delegateProxyStatus(DelegateProxyStatus.ToRemove);

        for (String seatId : seatIds) {
            // make sure other seats cannot be claimed any-more
            if (seatStore.removeSeatsIfUnclaimed(Set.of(seatId)) // seat not claimed
                || seatStore.getSeat(seatId) == null // seat does not exist
                || seatStore.getSeat(seatId).getDelegatingProxyId() == null // seat not yet added to unclaimed seats
            ) {
                // remove seat info
                seatStore.removeSeatInfo(seatId);
                delegateProxyBuilder.removeSeatId(seatId);
                logger.info("[{} {} {}] Removed seat", kv("specId", proxySpec.getId()), kv("delegateProxyId", delegateProxy.getProxy().getId()), kv("seatId", seatId));
            } else {
                logger.info("[{} {} {}] Cannot yet remove seat, it is still claimed", kv("specId", proxySpec.getId()), kv("delegateProxyId", delegateProxy.getProxy().getId()), kv("seatId", seatId));
            }
        }
        delegateProxyStore.updateDelegateProxy(delegateProxyBuilder.build());
    }

    private void reconcile() {
        long numPendingSeats = getNumPendingSeats();
        long num = seatStore.getNumUnclaimedSeats() + numPendingSeats - pendingDelegatingProxies.size();
        debug(String.format("Status: %s, Unclaimed: %s + PendingDelegate: %s - PendingDelegating: %s = %s -> minimum: %s",
            lastReconcileStatus, seatStore.getNumUnclaimedSeats(), numPendingSeats,
            pendingDelegatingProxies.size(), num, specExtension.minimumSeatsAvailable));

        if (num < specExtension.minimumSeatsAvailable) {
            if (proxySpec.getMaxTotalInstances() > -1 && seatStore.getNumSeats() >= proxySpec.getMaxTotalInstances()) {
                logWarn(String.format("Not scaling up: currently %s seats, scale up would create more than maximum number of instances: %s", seatStore.getNumSeats(), proxySpec.getMaxTotalInstances()));
                return;
            }
            lastReconcileStatus = ReconcileStatus.ScaleUp;
            long numToScaleUp = specExtension.minimumSeatsAvailable - num;
            scaleUp(MathUtil.divideAndCeil(numToScaleUp, specExtension.seatsPerContainer));
            lastScaleUp = Instant.now();
        } else if (numPendingSeats > 0) {
            // still scaling up
            lastReconcileStatus = ReconcileStatus.ScaleUp;
            lastScaleUp = Instant.now();
        } else if ((num - specExtension.minimumSeatsAvailable) >= specExtension.seatsPerContainer) {
            long numToScaleDown = (num - specExtension.minimumSeatsAvailable) / specExtension.seatsPerContainer;
            if (numToScaleDown <= 0) {
                throw new IllegalStateException("oops");
            }
            if (lastScaleUp != null) {
                long scaleUpDeltaMinutes = Duration.between(lastScaleUp, Instant.now()).toMinutes();
                if (scaleUpDeltaMinutes < specExtension.scaleDownDelay) {
                    logger.info(String.format("Not scaling down because last scaleUp was %s minutes ago (%s proxies to remove, delay is %s)", scaleUpDeltaMinutes, numToScaleDown, specExtension.scaleDownDelay));
                    return;
                }
            }
            lastReconcileStatus = ReconcileStatus.ScaleDown;
            scaleDown(numToScaleDown);
        } else {
            lastReconcileStatus = ReconcileStatus.Stable;
            debug("No scaling required");
        }
    }

    private void scaleUp(long numToScaleUp) {
        log(String.format("Scale up required, trying to create %s DelegateProxies", numToScaleUp));
        for (int i = 0; i < numToScaleUp; i++) {
            // store pending proxy here, so that this pending proxy is taking into account in the scaleUp function
            // in order to not overshoot
            String id = UUID.randomUUID().toString();
            Proxy.ProxyBuilder proxyBuilder = Proxy.builder();
            proxyBuilder.id(id);
            Proxy proxy = proxyBuilder.build();
            DelegateProxy delegateProxy = new DelegateProxy(proxy, Set.of(), DelegateProxyStatus.Pending, proxySpecHash);
            delegateProxyStore.addDelegateProxy(delegateProxy);
            log(delegateProxy, "Creating DelegateProxy");
            executor.submit(createDelegateProxyJob(delegateProxy));
        }
    }

    private Runnable createDelegateProxyJob(DelegateProxy originalDelegateProxy) {
        String id = originalDelegateProxy.getProxy().getId();
        return () -> {
            Proxy proxy = null;
            try {
                Proxy.ProxyBuilder proxyBuilder = originalDelegateProxy.getProxy().toBuilder();
                log(originalDelegateProxy, "Preparing DelegateProxy");

                proxyBuilder.targetId(id);
                proxyBuilder.status(ProxyStatus.New);
                proxyBuilder.specId(proxySpec.getId());
                long createdTimestamp = System.currentTimeMillis();
                proxyBuilder.createdTimestamp(createdTimestamp);
                // TODO add minimal set of runtimevalues
                proxyBuilder.addRuntimeValue(new RuntimeValue(DelegateProxyKey.inst, true), false);
                proxyBuilder.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, getPublicPath(id)), false);
                proxyBuilder.addRuntimeValue(new RuntimeValue(InstanceIdKey.inst, identifierService.instanceId), false);
                proxyBuilder.addRuntimeValue(new RuntimeValue(CreatedTimestampKey.inst, Long.toString(createdTimestamp)), false);
                proxyBuilder.addRuntimeValue(new RuntimeValue(ProxyIdKey.inst, id), false);
                if (identifierService.realmId != null) {
                    proxyBuilder.addRuntimeValue(new RuntimeValue(RealmIdKey.inst, identifierService.realmId), false);
                }
                proxyBuilder.addRuntimeValue(new RuntimeValue(ProxySpecIdKey.inst, proxySpec.getId()), false);

                proxy = proxyBuilder.build();
                DelegateProxy delegateProxy = originalDelegateProxy.toBuilder().proxy(proxy).build();
                delegateProxyStore.updateDelegateProxy(delegateProxy);

                SpecExpressionContext context = SpecExpressionContext.create(proxy, proxySpec);
                ProxySpec resolvedSpec = proxySpec.firstResolve(expressionResolver, context);
                context = context.copy(resolvedSpec, proxy);
                resolvedSpec = resolvedSpec.finalResolve(expressionResolver, context);

                // create container objects
                for (ContainerSpec containerSpec : resolvedSpec.getContainerSpecs()) {
                    Container.ContainerBuilder containerBuilder = Container.builder();
                    containerBuilder.index(containerSpec.getIndex());
                    Container container = containerBuilder.build();
                    container = runtimeValueService.addRuntimeValuesAfterSpel(containerSpec, container);
                    proxyBuilder.addContainer(container);
                }
                proxy = proxyBuilder.build();
                // TODO use startupLog ?
                log(delegateProxy, "Starting DelegateProxy");
                proxy = containerBackend.startProxy(null, proxy, resolvedSpec, new ProxyStartupLog.ProxyStartupLogBuilder());
                delegateProxy = originalDelegateProxy.toBuilder().proxy(proxy).build();
                delegateProxyStore.updateDelegateProxy(delegateProxy);

                if (!testStrategy.testProxy(proxy)) {
                    logWarn(delegateProxy, "Failed to start DelegateProxy: Container did not respond in time");
                    try {
                        containerBackend.stopProxy(proxy);
                    } catch (Throwable t) {
                        // log error, but ignore it otherwise
                        // most important is that we remove the proxy from memory
                        logWarn(delegateProxy, "Error while stopping failed DelegateProxy");
                    }
                    delegateProxyStore.removeDelegateProxy(id);
                    globalEventLoop.schedule(this::reconcile);
                    return;
                }

                proxy = proxy.toBuilder()
                    .startupTimestamp(System.currentTimeMillis())
                    .status(ProxyStatus.Up)
                    .build();

                DelegateProxy.DelegateProxyBuilder delegateProxyBuilder = originalDelegateProxy.toBuilder()
                    .delegateProxyStatus(DelegateProxyStatus.Available)
                    .proxy(proxy);

                List<Seat> seats = new ArrayList<>();
                for (int i = 0; i < specExtension.seatsPerContainer; i++) {
                    Seat seat = new Seat(proxy.getId());
                    seats.add(seat);
                    delegateProxyBuilder.addSeatId(seat.getId());
                    log(seat, "Created Seat");
                }

                delegateProxy = delegateProxyBuilder.build();
                delegateProxyStore.updateDelegateProxy(delegateProxy);

                // only make seats available if DelegateProxy has been completely updated in store
                // if an error happens here the seatIds are already stored and can be removed again
                for (Seat seat : seats) {
                    seatStore.addSeat(seat);
                }

                logService.attachToOutput(proxy);
                log(delegateProxy, "Started DelegateProxy");

                for (int i = 0; i < specExtension.seatsPerContainer; i++) {
                    if (!pendingDelegatingProxies.isEmpty()) {
                        String intendedProxyId = pendingDelegatingProxies.remove(0);
                        applicationEventPublisher.publishEvent(new SeatAvailableEvent(proxySpec.getId(), intendedProxyId));
                    }
                }
            } catch (ProxyFailedToStartException t) {
                logError(originalDelegateProxy, t, "Failed to start DelegateProxy");
                try {
                    // try to remove container if any
                    containerBackend.stopProxy(t.getProxy());
                } catch (Throwable t2) {
                    // log error, but ignore it otherwise
                    // most important is that we remove the proxy from memory
                    logError(originalDelegateProxy, t, "Error while stopping failed DelegateProxy");
                }
                // remove seats and other data + trigger reconcile
                globalEventLoop.schedule(() -> markDelegateProxyForRemoval(id));
                globalEventLoop.schedule(this::reconcile);
            } catch (Throwable t) {
                logError(originalDelegateProxy, t, "Failed to start DelegateProxy");
                if (proxy != null) {
                    try {
                        // try to remove container if any
                        containerBackend.stopProxy(proxy);
                    } catch (Throwable t2) {
                        // log error, but ignore it otherwise
                        // most important is that we remove the proxy from memory
                        logError(originalDelegateProxy, t, "Error while stopping failed DelegateProxy");
                    }
                }
                // remove seats and other data + trigger reconcile
                globalEventLoop.schedule(() -> markDelegateProxyForRemoval(id));
                globalEventLoop.schedule(this::reconcile);
            }
        };
    }

    private void scaleDown(long numToScaleDown) {
        log(String.format("Scale down required, trying to remove %s DelegateProxies", numToScaleDown));
        List<DelegateProxy> delegateProxiesToRemove = new ArrayList<>();
        // first find proxies of which all seats are unclaimed and already remove these from the unclaimed store
        try {
            for (DelegateProxy delegateProxy : delegateProxyStore.getAllDelegateProxies()) {
                if (delegateProxy.getDelegateProxyStatus() == DelegateProxyStatus.Available && seatStore.removeSeatsIfUnclaimed(delegateProxy.getSeatIds())) {
                    delegateProxiesToRemove.add(delegateProxy);
                    if (delegateProxiesToRemove.size() == numToScaleDown) {
                        break;
                    }
                }
            }
        } catch (RedisSeatStore.SeatClaimedDuringRemovalException ex) {
            log("Stopping scale down because a seat was claimed");
        }
        if (delegateProxiesToRemove.isEmpty()) {
            log("No proxy found to remove during scale-down.");
            return;
        }
        for (DelegateProxy delegateProxy : delegateProxiesToRemove) {
            log(delegateProxy, "Selected DelegateProxy for removal during scale-down");
        }
        // only now remove the proxies (this takes the most time)
        removeDelegateProxies(delegateProxiesToRemove);
    }

    private void cleanup() {
        if (!lastReconcileStatus.equals(ReconcileStatus.Stable) && !lastReconcileStatus.equals(ReconcileStatus.ScaleDown)) {
            return;
        }
        // remove proxies that are scheduled to remove and are fully unclaimed
        Collection<DelegateProxy> allDelegateProxies = delegateProxyStore.getAllDelegateProxies();
        List<DelegateProxy> delegateProxiesToRemove = new ArrayList<>();
        try {
            for (DelegateProxy delegateProxy : allDelegateProxies) {
                if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.ToRemove)) {
                    if (delegateProxy.getSeatIds().isEmpty() || seatStore.removeSeatsIfUnclaimed(delegateProxy.getSeatIds())) {
                        delegateProxiesToRemove.add(delegateProxy);
                    } else {
                        debug(delegateProxy, "DelegateProxy marked for removal but still has claimed seats");
                    }
                }
            }
        } catch (RedisSeatStore.SeatClaimedDuringRemovalException ex) {
            debug("Stopping cleanup because a seat was claimed");
        }
        // only now remove the proxies (this takes the most time)
        removeDelegateProxies(delegateProxiesToRemove);
    }

    private void removeDelegateProxies(List<DelegateProxy> delegateProxies) {
        for (DelegateProxy delegateProxy : delegateProxies) {
            log(delegateProxy, "Stopping DelegateProxy");
            try {
                containerBackend.stopProxy(delegateProxy.getProxy());
            } catch (Throwable t) {
                logError(delegateProxy, t, "Failed to stop delegateProxy");
            }
            try {
                delegateProxyStore.removeDelegateProxy(delegateProxy.getProxy().getId());
            } catch (Throwable t) {
                logError(delegateProxy, t, "Failed to remove delegateProxy");
            }
            try {
                logService.detach(delegateProxy.getProxy());
            } catch (Throwable t) {
                logError(delegateProxy, t, "Failed to de-attach log collector");
            }
        }
    }

    @Async
    @EventListener
    public void onLeaderGranted(OnGrantedEvent event) {
        globalEventLoop.schedule(this::processOnLeaderGranted);
    }

    @Async
    @EventListener
    public void onLeaderRevoked(OnRevokedEvent event) {
        executor.shutdownNow();
    }

    /**
     * Runs when this ShinyProxy server becomes the leader, potentially with new config.
     * Should be run using the event loop.
     */
    private void processOnLeaderGranted() {
        // server is now the leader...
        // ... check if running proxies are using the latest config
        for (DelegateProxy delegateProxy : delegateProxyStore.getAllDelegateProxies()) {
            if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.Pending)) {
                log(delegateProxy, "Pending DelegateProxy not created by this instance, marking for removal");
                markDelegateProxyForRemoval(delegateProxy.getProxy().getId());
            } else if (!delegateProxy.getProxySpecHash().equals(proxySpecHash)) {
                log(delegateProxy, "DelegateProxy not created by this config instance, marking for removal");
                markDelegateProxyForRemoval(delegateProxy.getProxy().getId());
            }
        }
        // ... attach log collectors
        for (DelegateProxy delegateProxy : delegateProxyStore.getAllDelegateProxies()) {
            if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.Available)) {
                logService.attachToOutput(delegateProxy.getProxy());
            }
        }
        // note: onLeaderRevoked the streams are detached by the LogService
        globalEventLoop.schedule(this::reconcile);
    }

    public ProxySpec getSpec() {
        return proxySpec;
    }

    private String getProxySpecHash(ProxySpec proxySpec) {
        return Sha1.hash(proxySpec);
    }

    private void removeSeat(DelegateProxy delegateProxy, String seatId) {
        delegateProxy = delegateProxy.toBuilder().removeSeatId(seatId).build();
        delegateProxyStore.updateDelegateProxy(delegateProxy);
        seatStore.removeSeatInfo(seatId);
        logger.info("[{} {} {}] Removed seat", kv("specId", proxySpec.getId()), kv("delegateProxyId", delegateProxy.getProxy().getId()), kv("seatId", seatId));
    }

    public Long getNumPendingSeats() {
        return delegateProxyStore.getAllDelegateProxies()
            .stream()
            .filter(it -> it.getDelegateProxyStatus().equals(DelegateProxyStatus.Pending))
            .count()
            * specExtension.seatsPerContainer;
    }

    public Long getNumUnclaimedSeats() {
        return seatStore.getNumUnclaimedSeats();
    }

    public Long getNumClaimedSeats() {
        return seatStore.getNumClaimedSeats();
    }

    private String getPublicPath(String targetId) {
        return publicPathPrefix + targetId + "/";
    }

    private void debug(String message) {
        logger.debug("[{}] " + message, kv("specId", proxySpec.getId()));
    }

    private void log(String message) {
        logger.info("[{}] " + message, kv("specId", proxySpec.getId()));
    }

    private void logWarn(String message) {
        logger.warn("[{}] " + message, kv("specId", proxySpec.getId()));
    }

    private void logError(String message, Throwable throwable) {
        logger.error("[{}] " + message, kv("specId", proxySpec.getId()), throwable);
    }

    private void debug(DelegateProxy delegateProxy, String message) {
        logger.debug("[{} {}] " + message, kv("specId", proxySpec.getId()), kv("delegateProxyId", delegateProxy.getProxy().getId()));
    }

    private void log(DelegateProxy delegateProxy, String message) {
        logger.info("[{} {}] " + message, kv("specId", proxySpec.getId()), kv("delegateProxyId", delegateProxy.getProxy().getId()));
    }

    private void logWarn(DelegateProxy delegateProxy, String message) {
        logger.warn("[{} {}] " + message, kv("specId", proxySpec.getId()), kv("delegateProxyId", delegateProxy.getProxy().getId()));
    }

    private void logError(DelegateProxy delegateProxy, Throwable throwable, String message) {
        if (delegateProxy.getProxy() != null) {
            logger.error("[{} {}] " + message, kv("delegateProxyId", delegateProxy.getProxy().getId()), kv("specId", proxySpec.getId()), throwable);
        } else {
            logger.error("[{} {}] " + message, kv("delegateProxyId", null), kv("specId", proxySpec.getId()), throwable);
        }
    }

    private void log(Seat seat, String message) {
        logger.info("[{} {} {}] " + message, kv("specId", proxySpec.getId()), kv("delegateProxyId", seat.getDelegateProxyId()), kv("seatId", seat.getId()));
    }

    private enum ReconcileStatus {
        Stable,
        ScaleUp,
        ScaleDown
    }

}
