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
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.dispatcher.IProxyDispatcher;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.memory.MemorySeatStore;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.RuntimeValueService;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class ProxySharingDispatcher implements IProxyDispatcher {

    private final IContainerBackend containerBackend;
    private final ProxySpec proxySpec;
    private final SpecExpressionResolver expressionResolver;
    private final RuntimeValueService runtimeValueService;

    private final ConcurrentHashMap<String, DelegateProxy> delegateProxies = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ISeatStore seatStore = new MemorySeatStore();

    private static String publicPathPrefix = "/api/route/";

    private final Integer minimumSeatsAvailable;
    private final Integer maximumSeatsAvailable;

    private final LinkedBlockingQueue<Event> channel = new LinkedBlockingQueue<>();

    private final Thread eventProcessor;

    private final List<String> pendingDelegateProxies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pendingDelegatingProxies = Collections.synchronizedList(new ArrayList<>());

    private final IProxyTestStrategy testStrategy;

    public static void setPublicPathPrefix(String publicPathPrefix) {
        ProxySharingDispatcher.publicPathPrefix = publicPathPrefix;
    }

    public ProxySharingDispatcher(IContainerBackend containerBackend, ProxySpec proxySpec, SpecExpressionResolver expressionResolver, RuntimeValueService runtimeValueService, IProxyTestStrategy testStrategy) {
        this.containerBackend = containerBackend;
        this.proxySpec = proxySpec;
        this.expressionResolver = expressionResolver;
        this.runtimeValueService = runtimeValueService;
        this.minimumSeatsAvailable = proxySpec.getSpecExtension(ProxySharingSpecExtension.class).minimumSeatsAvailable;
        this.maximumSeatsAvailable = proxySpec.getSpecExtension(ProxySharingSpecExtension.class).maximumSeatsAvailable;
        this.testStrategy = testStrategy;

        eventProcessor = new Thread(new EventProcessor(channel, this));
        eventProcessor.start();
        channel.add(Event.RECONCILE);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                channel.add(Event.RECONCILE);
            }
        }, 0, 10_000);
    }

    public static boolean supportSpec(ProxySpec proxySpec) {
        return proxySpec.getSpecExtension(ProxySharingSpecExtension.class).minimumSeatsAvailable != null;
    }

    @Override
    public Proxy startProxy(Authentication user, Proxy proxy, ProxySpec spec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ProxyFailedToStartException {
        Seat seat = seatStore.claimSeat(spec.getId(), proxy.getId()).orElse(null);
        if (seat == null) {
            logger.info("Seat not immediately available");
            pendingDelegatingProxies.add(proxy.getId());
            channel.add(Event.RECONCILE); // re-trigger reconcile, so we are sure a scale up is scheduled
            // no seat available, busy loop until one becomes available
            // TODO replace by native async code, taking into account HA and multi seat per container
            for (int i = 0; i < 600; i++) {
                seat = seatStore.claimSeat(proxy.getId()).orElse(null);
                if (seat != null) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    pendingDelegatingProxies.remove(proxy.getId());
                    throw new RuntimeException(e);
                }
            }
            pendingDelegatingProxies.remove(proxy.getId());
            if (seat == null) {
                throw new IllegalStateException("Could not claim a seat");
            }
        }

        Proxy delegateProxy = delegateProxies.get(seat.getTargetId()).proxy;

        Proxy.ProxyBuilder resultProxy = proxy.toBuilder();
        resultProxy.targetId(delegateProxy.getId());
        resultProxy.addTargets(delegateProxy.getTargets());
        String publicPath = proxy.getRuntimeObjectOrNull(PublicPathKey.inst);
        if (publicPath != null) {
            resultProxy.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, publicPath.replaceAll(proxy.getId(), delegateProxy.getId())), true);
        }
        resultProxy.addRuntimeValue(new RuntimeValue(SeatIdRuntimeValue.inst, seat.getId()), true);

        channel.add(Event.RECONCILE); // re-trigger reconcile

        return resultProxy.build();
    }

    @Override
    public void stopProxy(Proxy proxy) throws ContainerProxyException {
        String seatId = proxy.getRuntimeValue(SeatIdRuntimeValue.inst);
        if (seatId == null) {
            throw new IllegalStateException("Not seat id runtimevalue"); // TODO
        }
        seatStore.releaseSeat(seatId);
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

    private void reconcile() {
        Integer seatsAvailable = seatStore.getNumUnclaimedSeats() + pendingDelegateProxies.size();
        Integer seatsRequired = minimumSeatsAvailable + pendingDelegatingProxies.size();
        if (seatsAvailable < seatsRequired) {
            int amountToScaleUp = seatsRequired - seatsAvailable;
            logger.info("Scale up required, needing " + amountToScaleUp);
            for (int i = 0; i < amountToScaleUp; i++) {
                String id = UUID.randomUUID().toString();
                pendingDelegateProxies.add(id);
                executor.submit(createDelegateProxyJob(id));
            }
        } else if (seatsAvailable > maximumSeatsAvailable) {
            int amountToScaleDown = seatsAvailable - maximumSeatsAvailable;
            logger.info("Scale down required, removing " + amountToScaleDown);
            for (int i = 0; i < amountToScaleDown; i++) {
                if (!removeDelegateProxy()) {
                    logger.info("Full Scale down not possible");
                    break;
                }
            }
        } else {
            logger.info("No scale up required");
        }
    }

    private boolean removeDelegateProxy() {
        for (DelegateProxy delegateProxy : delegateProxies.values()) {
            if (seatStore.removeSeats(delegateProxy.seatIds)) {
                containerBackend.stopProxy(delegateProxy.proxy);
                delegateProxies.remove(delegateProxy.proxy.getId());
                logger.info("Removed one delegate proxy " + delegateProxy.proxy.getId());
                return true;
            }
        }
        return false;
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
                delegateProxies.put(proxy.getId(), new DelegateProxy(proxy, Set.of()));

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

                if (!testStrategy.testProxy(proxy)) {
                    logger.info("Failed to start delegate proxy (did not come online)" + id); // TODO
                }

                Seat seat = new Seat(proxy.getId());
                delegateProxies.put(proxy.getId(), new DelegateProxy(proxy, Set.of(seat.getId())));
                seatStore.addSeat(seat);
                logger.info("Started DelegateProxy " + id);
            } catch (ProxyFailedToStartException ex) {
                logger.error("Failed to start delegate proxy", ex);
            } finally {
                pendingDelegateProxies.remove(id);
                channel.add(Event.RECONCILE); // re-trigger reconcile in-case startup failed
            }
        };
    }

    private enum Event {
        RECONCILE
    }

    private static class EventProcessor implements Runnable {

        private final LinkedBlockingQueue<Event> channel;
        private final ProxySharingDispatcher dispatcher;

        public EventProcessor(LinkedBlockingQueue<Event> channel, ProxySharingDispatcher dispatcher) {
            this.channel = channel;
            this.dispatcher = dispatcher;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Event event = channel.take();
                    if (event == Event.RECONCILE) {
                        try {
                            dispatcher.reconcile();
                        } catch (Exception ex) {
//                            logger.error("Error", ex);
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

    }

    private static class DelegateProxy {

        private final Proxy proxy;
        private final Set<String> seatIds;

        private DelegateProxy(Proxy proxy, Set<String> seatIds) {
            this.proxy = proxy;
            this.seatIds = seatIds;
        }

        public Proxy getProxy() {
            return proxy;
        }

        public Set<String> getSeatIds() {
            return seatIds;
        }
    }

}
