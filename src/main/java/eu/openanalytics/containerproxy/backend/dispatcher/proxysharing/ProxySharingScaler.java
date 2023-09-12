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
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ISpecExtension;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.RuntimeValueService;
import eu.openanalytics.containerproxy.service.leader.GlobalEventLoopService;
import eu.openanalytics.containerproxy.service.leader.ILeaderService;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.util.Sha1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class ProxySharingScaler {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final IContainerBackend containerBackend;
    private final IDelegateProxyStore delegateProxyStore;
    private final IProxyTestStrategy testStrategy;
    private final ISeatStore seatStore;
    private final Integer maximumSeatsAvailable;
    private final Integer minimumSeatsAvailable;
    private final List<String> pendingDelegatingProxies = Collections.synchronizedList(new ArrayList<>());
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ProxySpec proxySpec;
    private final RuntimeValueService runtimeValueService;
    private final SpecExpressionResolver expressionResolver;
    private final IdentifierService identifierService;

    private static String publicPathPrefix = "/api/route/";
    private final GlobalEventLoopService globalEventLoop;
    private final String proxySpecHash;
    private final ILeaderService leaderService;
    private ReconcileStatus lastReconcileStatus = ReconcileStatus.Stable;

    public static void setPublicPathPrefix(String publicPathPrefix) {
        ProxySharingScaler.publicPathPrefix = publicPathPrefix;
    }

    // TODO add cleanup of proxies that never became ready

    public ProxySharingScaler(ISeatStore seatStore, ProxySpec proxySpec, IDelegateProxyStore delegateProxyStore, IContainerBackend containerBackend, SpecExpressionResolver expressionResolver,
                              RuntimeValueService runtimeValueService, IProxyTestStrategy testStrategy, GlobalEventLoopService globalEventLoop, IdentifierService identifierService, ILeaderService leaderService) {
        this.proxySpec = proxySpec;
        this.minimumSeatsAvailable = proxySpec.getSpecExtension(ProxySharingSpecExtension.class).minimumSeatsAvailable;
        this.maximumSeatsAvailable = proxySpec.getSpecExtension(ProxySharingSpecExtension.class).maximumSeatsAvailable;
        this.globalEventLoop = globalEventLoop;
        this.seatStore = seatStore;
        this.delegateProxyStore = delegateProxyStore;
        this.containerBackend = containerBackend;
        this.expressionResolver = expressionResolver;
        this.runtimeValueService = runtimeValueService;
        this.testStrategy = testStrategy;
        this.identifierService = identifierService;
        this.leaderService = leaderService;
        proxySpecHash = getProxySpecHash(proxySpec);
        globalEventLoop.addCallback("ProxySharingScaler::cleanup", this::cleanup);
        globalEventLoop.addCallback("ProxySharingScaler:reconcile", this::reconcile);
    }

    @Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
    public void scheduleCleanup() {
        globalEventLoop.schedule("ProxySharingScaler::cleanup");
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void scheduleReconcile() {
        globalEventLoop.schedule("ProxySharingScaler:reconcile");
    }

    @EventListener
    public void onPendingProxyEvent(PendingProxyEvent pendingProxyEvent) {
        if (!Objects.equals(pendingProxyEvent.getSpecId(), proxySpec.getId()) || !leaderService.isLeader()) {
            // only handle events for this spec
            return;
        }
        pendingDelegatingProxies.add(pendingProxyEvent.getProxyId());
        globalEventLoop.schedule("ProxySharingScaler:reconcile");
    }

    @EventListener
    public void onSeatClaimedEvent(SeatClaimedEvent seatClaimedEvent) {
        if (!Objects.equals(seatClaimedEvent.getSpecId(), proxySpec.getId()) || !leaderService.isLeader()) {
            // only handle events for this spec
            return;
        }
        globalEventLoop.schedule("ProxySharingScaler:reconcile");
        // if the seat was claimed by a pending proxy we need to remove it from the pendingDelegatingProxies
        pendingDelegatingProxies.remove(seatClaimedEvent.getClaimingProxyId());
    }

    @EventListener
    public void onSeatReleasedEvent(SeatReleasedEvent seatReleasedEvent) {
        if (!Objects.equals(seatReleasedEvent.getSpecId(), proxySpec.getId()) || !leaderService.isLeader()) {
            // only handle events for this spec
            return;
        }

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
        if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.Available)) {
            seatStore.addToUnclaimedSeats(seatId);
        } else if (delegateProxy.getDelegateProxyStatus().equals(DelegateProxyStatus.ToRemove)) {
            if (lastReconcileStatus.equals(ReconcileStatus.ScaleUp)) {
                log(delegateProxy, String.format("Re-adding seat %s to unclaimed seath although it's marked for removal because there are pending delegating proxies", seatId));
                seatStore.addToUnclaimedSeats(seatId);
            } else {
                // seat no longer needed, remove it
                removeSeat(delegateProxy, seatId);
            }
        }
    }

    private void reconcile() {
        long numPendingSeats = getNumPendingSeats();
        long numToRemove = getNumClaimableToRemove();
        long num = seatStore.getNumUnclaimedSeats() + numPendingSeats - pendingDelegatingProxies.size() - numToRemove;
        debug(String.format("Status: %s, Unclaimed: %s + PendingDelegate: %s - PendingDelegating: %s - UnclaimedToRemove: %s = %s -> minimum: %s",
            lastReconcileStatus, seatStore.getNumUnclaimedSeats(), numPendingSeats,
            pendingDelegatingProxies.size(), numToRemove, num, minimumSeatsAvailable));
        if (num < minimumSeatsAvailable) {
            lastReconcileStatus = ReconcileStatus.ScaleUp;
            long numToScaleUp = minimumSeatsAvailable - num;
            scaleUp(numToScaleUp);
        } else if (numPendingSeats > 0) {
            // still scaling up
            lastReconcileStatus = ReconcileStatus.ScaleUp;
        } else if (num > maximumSeatsAvailable) {
            lastReconcileStatus = ReconcileStatus.ScaleDown;
            long numToScaleDown = num - maximumSeatsAvailable;
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
            try {
                Proxy.ProxyBuilder proxyBuilder = originalDelegateProxy.getProxy().toBuilder();
                log(originalDelegateProxy, "Preparing DelegateProxy");

                proxyBuilder.targetId(id);
                proxyBuilder.status(ProxyStatus.New);
                proxyBuilder.specId(proxySpec.getId());
                proxyBuilder.createdTimestamp(System.currentTimeMillis());
                // TODO add minimal set of runtimevalues
                proxyBuilder.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, publicPathPrefix + id), false);
                proxyBuilder.addRuntimeValue(new RuntimeValue(InstanceIdKey.inst, identifierService.instanceId), false);

                Proxy proxy = proxyBuilder.build();
                DelegateProxy delegateProxy = originalDelegateProxy.toBuilder().delegateProxyStatus(DelegateProxyStatus.Pending).proxy(proxy).build();
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
                    globalEventLoop.schedule("ProxySharingScaler:reconcile");
                    return;
                }

                proxy = proxy.toBuilder()
                    .startupTimestamp(System.currentTimeMillis())
                    .status(ProxyStatus.Up)
                    .build();

                Seat seat = new Seat(proxy.getId());
                delegateProxy = originalDelegateProxy.toBuilder()
                    .delegateProxyStatus(DelegateProxyStatus.Available)
                    .proxy(proxy)
                    .seatIds(Set.of(seat.getId()))
                    .build();
                delegateProxyStore.updateDelegateProxy(delegateProxy);
                seatStore.addSeat(seat);
                log(seat, "Created Seat");
                log(delegateProxy, "Started DelegateProxy");
            } catch (ProxyFailedToStartException t) {
                logError(originalDelegateProxy, t, "Failed to start DelegateProxy");
                try {
                    containerBackend.stopProxy(t.getProxy());
                } catch (Throwable t2) {
                    // log error, but ignore it otherwise
                    // most important is that we remove the proxy from memory
                    logError(originalDelegateProxy, t, "Error while stopping failed DelegateProxy");
                }
                delegateProxyStore.removeDelegateProxy(id);
                globalEventLoop.schedule("ProxySharingScaler:reconcile");
            } catch (Throwable t) {
                logError(originalDelegateProxy, t, "Failed to start DelegateProxy");
                delegateProxyStore.removeDelegateProxy(id);
                globalEventLoop.schedule("ProxySharingScaler:reconcile");
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
        log(String.format("Stopping %s DelegateProxies", delegateProxiesToRemove.size()));
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
        if (!delegateProxiesToRemove.isEmpty()) {
            log(String.format("Removing %s DelegateProxies that are using outdated spec", delegateProxiesToRemove.size()));
            // only now remove the proxies (this takes the most time)
            removeDelegateProxies(delegateProxiesToRemove);
        }
    }

    private void removeDelegateProxies(List<DelegateProxy> delegateProxies) {
        for (DelegateProxy delegateProxy : delegateProxies) {
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
        }
    }

    @Async
    @EventListener
    public void compareConfigs(OnGrantedEvent event) {
        // server is now the leader, check if running proxies are using the latest config
        for (DelegateProxy delegateProxy : delegateProxyStore.getAllDelegateProxies()) {
            if (delegateProxy.getProxySpecHash().equals(proxySpecHash)) {
                log(delegateProxy, "DelegateProxy created by this config instance");
            } else {
                log(delegateProxy, "DelegateProxy not created by this config instance, marking for removal");
                delegateProxy = delegateProxy.toBuilder().delegateProxyStatus(DelegateProxyStatus.ToRemove).build();
                delegateProxyStore.updateDelegateProxy(delegateProxy);
                for (String seatId : delegateProxy.getSeatIds()) {
                    if (seatStore.removeSeatsIfUnclaimed(Set.of(seatId))) {
                        // seat not claimed, we can already remove it
                        removeSeat(delegateProxy, seatId);
                    }
                }
            }
        }
    }

    private String getProxySpecHash(ProxySpec proxySpec) {
        // remove ProxySharing SpecExtension, so it's ignored in the hash
        Map<String, ISpecExtension> specExtensions = new HashMap<>(proxySpec.getSpecExtensions());
        specExtensions.remove(ProxySharingSpecExtension.class.getName());
        ProxySpec canonicalSpec = proxySpec
            .toBuilder()
            .specExtensions(specExtensions)
            .build();
        return Sha1.hash(canonicalSpec);
    }

    private void removeSeat(DelegateProxy delegateProxy, String seatId) {
        Set<String> seatIds = delegateProxy.getSeatIds();
        seatIds.remove(seatId);
        delegateProxy = delegateProxy.toBuilder().seatIds(seatIds).build();
        delegateProxyStore.updateDelegateProxy(delegateProxy);
        seatStore.removeSeatInfo(seatId);
        logger.info("[{} {} {}] Removed seat", kv("specId", proxySpec.getId()), kv("delegateProxyId", delegateProxy.getProxy().getId()), kv("seatId", seatId));
    }

    public Long getNumPendingSeats() {
        return delegateProxyStore.getAllDelegateProxies().stream().filter(it -> it.getDelegateProxyStatus().equals(DelegateProxyStatus.Pending)).count();
    }

    /**
     * @return the number of delegateProxies that are marked to remove and can be claimed (and therefore need to be replaced)
     */
    private Long getNumClaimableToRemove() {
        return delegateProxyStore.getAllDelegateProxies()
            .stream()
            .filter(it -> it.getDelegateProxyStatus().equals(DelegateProxyStatus.ToRemove) && proxyToRemoveIsClaimable(it)).count();
    }

    private boolean proxyToRemoveIsClaimable(DelegateProxy delegateProxy) {
        if (delegateProxy.getSeatIds().isEmpty()) { // TODO test
            // this delegateProxy contains no seat -> this delegateProxy is not claimable
            return false;
        }
        for (String seatId : delegateProxy.getSeatIds()) {
            if (!seatStore.isSeatClaimable(seatId)) { // TODO test
                // a seat is currently not claimable -> this delegateProxy is not claimable
                return false;
            }
        }
        return true;
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
            logger.error("[{} {} {}] " + message, kv("proxyId", delegateProxy.getProxy().getId()), kv("specId", proxySpec.getId()), throwable);
        } else {
            logger.error("[{} {} {}] " + message, kv("proxyId", null), kv("specId", proxySpec.getId()), throwable);
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
