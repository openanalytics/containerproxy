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
package eu.openanalytics.containerproxy.backend.dispatcher;

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingDispatcher;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingScaler;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.IProxySharingStoreFactory;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.LogService;
import eu.openanalytics.containerproxy.service.RuntimeValueService;
import eu.openanalytics.containerproxy.service.leader.GlobalEventLoopService;
import eu.openanalytics.containerproxy.service.leader.ILeaderService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProxyDispatcherService {

    private final Map<String, IProxyDispatcher> dispatchers = new HashMap<>();
    private final IProxySpecProvider proxySpecProvider;
    private final IContainerBackend containerBackend;
    private final SpecExpressionResolver expressionResolver;
    private final RuntimeValueService runtimeValueService;
    private final IProxyTestStrategy proxyTestStrategy;
    private final IProxySharingStoreFactory storeFactory;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ConfigurableListableBeanFactory beanFactory;
    private final GlobalEventLoopService globalEventLoop;
    private final IdentifierService identifierService;
    private final ILeaderService leaderService;
    private final LogService logService;
    private final IProxyStore proxyStore;

    public ProxyDispatcherService(IProxySpecProvider proxySpecProvider,
                                  IContainerBackend containerBackend,
                                  SpecExpressionResolver expressionResolver,
                                  RuntimeValueService runtimeValueService,
                                  IProxyTestStrategy proxyTestStrategy,
                                  IProxySharingStoreFactory storeFactory,
                                  ApplicationEventPublisher applicationEventPublisher,
                                  ConfigurableListableBeanFactory beanFactory,
                                  GlobalEventLoopService globalEventLoop,
                                  IdentifierService identifierService,
                                  ILeaderService leaderService,
                                  LogService logService,
                                  IProxyStore proxyStore) {
        this.proxySpecProvider = proxySpecProvider;
        this.containerBackend = containerBackend;
        this.expressionResolver = expressionResolver;
        this.runtimeValueService = runtimeValueService;
        this.proxyTestStrategy = proxyTestStrategy;
        this.storeFactory = storeFactory;
        this.applicationEventPublisher = applicationEventPublisher;
        this.beanFactory = beanFactory;
        this.globalEventLoop = globalEventLoop;
        this.identifierService = identifierService;
        this.leaderService = leaderService;
        this.logService = logService;
        this.proxyStore = proxyStore;
    }

    @PostConstruct
    public void init() {
        DefaultProxyDispatcher defaultProxyDispatcher = new DefaultProxyDispatcher(containerBackend);
        for (ProxySpec proxySpec : proxySpecProvider.getSpecs()) {
            if (ProxySharingDispatcher.supportSpec(proxySpec)) {
                ISeatStore seatStore = storeFactory.createSeatStore(proxySpec.getId());
                IDelegateProxyStore delegateProxyStore = storeFactory.createDelegateProxyStore(proxySpec.getId());

                ProxySharingScaler proxySharingScaler = new ProxySharingScaler(
                    seatStore,
                    proxySpec,
                    delegateProxyStore,
                    containerBackend,
                    expressionResolver,
                    runtimeValueService,
                    proxyTestStrategy,
                    globalEventLoop,
                    identifierService,
                    leaderService,
                    logService,
                    applicationEventPublisher);

                proxySharingScaler = (ProxySharingScaler) beanFactory.initializeBean(proxySharingScaler,"proxySharingScaler_" + proxySpec.getId());
                beanFactory.registerSingleton("proxySharingScaler_" + proxySpec.getId(), proxySharingScaler);

                ProxySharingDispatcher proxySharingDispatcher = new ProxySharingDispatcher(
                    proxySpec,
                    delegateProxyStore,
                    seatStore,
                    applicationEventPublisher,
                    proxySharingScaler,
                    proxyStore
                );

                proxySharingDispatcher = (ProxySharingDispatcher) beanFactory.initializeBean(proxySharingDispatcher,"proxySharingDispatcher_" + proxySpec.getId());
                beanFactory.registerSingleton("proxySharingDispatcher_" + proxySpec.getId(), proxySharingDispatcher);

                dispatchers.put(proxySpec.getId(), proxySharingDispatcher);

            } else {
                dispatchers.put(proxySpec.getId(), defaultProxyDispatcher);
            }
        }
    }

    public IProxyDispatcher getDispatcher(String specId) {
        return dispatchers.get(specId);
    }

}
