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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxySharingDispatcher implements IProxyDispatcher {

    private final IContainerBackend containerBackend;
    private final ProxySpec proxySpec;
    private final SpecExpressionResolver expressionResolver;
    private final RuntimeValueService runtimeValueService;

    private final ConcurrentHashMap<String, Proxy> delegateProxies = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ISeatStore seatStore = new MemorySeatStore();

    public ProxySharingDispatcher(IContainerBackend containerBackend, ProxySpec proxySpec, SpecExpressionResolver expressionResolver, RuntimeValueService runtimeValueService) {
        this.containerBackend = containerBackend;
        this.proxySpec = proxySpec;
        this.expressionResolver = expressionResolver;
        this.runtimeValueService = runtimeValueService;

        for (int i = 0; i < 10; i++) {
            executor.submit(createDelegateProxyJob());
        }
    }

    public static boolean supportSpec(ProxySpec proxySpec) {
        return true;
    }

    @Override
    public Proxy startProxy(Authentication user, Proxy proxy, ProxySpec spec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ProxyFailedToStartException {
        Seat seat = seatStore.claimSeat(spec.getId(), proxy.getId()).orElseThrow();
        Proxy delegateProxy = delegateProxies.get(seat.getTargetId());

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
        String seatId = proxy.getRuntimeValue(SeatIdRuntimeValue.inst);
        if (seatId == null) {
            throw new IllegalStateException("Not seat id runtimevalue"); // TODO
        }
        seatStore.releaseSeat(proxy.getSpecId(), seatId);
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

    private Runnable createDelegateProxyJob() {
        return () -> {
            Proxy.ProxyBuilder proxyBuilder = Proxy.builder();
            String id = UUID.randomUUID().toString();

            logger.info("Creating DelegateProxy " + id);

            proxyBuilder.id(id);
            proxyBuilder.targetId(id);
            proxyBuilder.status(ProxyStatus.New);
//        proxyBuilder.userId("SHINYPROXY_CONTAINER_SHARING"); // TODO
            proxyBuilder.specId(proxySpec.getId());
            proxyBuilder.createdTimestamp(System.currentTimeMillis());
            // TODO add minimal set of runtimevalues
            proxyBuilder.addRuntimeValue(new RuntimeValue(PublicPathKey.inst, "/app_proxy/" + id), false); // TODO

            // create container objects
            Proxy proxy = proxyBuilder.build();
            delegateProxies.put(proxy.getId(), proxy);

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
            delegateProxies.put(proxy.getId(), proxy);
            seatStore.addSeat(proxySpec.getId(), new Seat(proxy.getId()));
            logger.info("Started DelegateProxy " + id);
        };
    }

}
