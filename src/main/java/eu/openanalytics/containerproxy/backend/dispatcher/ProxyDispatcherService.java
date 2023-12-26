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

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingDispatcher;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingScaler;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.IProxySharingStoreFactory;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProxyDispatcherService {

    private final Map<String, IProxyDispatcher> dispatchers = new HashMap<>();
    private final IProxySpecProvider proxySpecProvider;
    private final IProxySharingStoreFactory storeFactory;
    private final ConfigurableListableBeanFactory beanFactory;
    private final DefaultProxyDispatcher defaultProxyDispatcher;

    public ProxyDispatcherService(IProxySpecProvider proxySpecProvider,
                                  IProxySharingStoreFactory storeFactory,
                                  ConfigurableListableBeanFactory beanFactory,
                                  DefaultProxyDispatcher defaultProxyDispatcher) {
        this.proxySpecProvider = proxySpecProvider;
        this.storeFactory = storeFactory;
        this.beanFactory = beanFactory;
        this.defaultProxyDispatcher = defaultProxyDispatcher;
    }

    @PostConstruct
    public void init() {
        for (ProxySpec proxySpec : proxySpecProvider.getSpecs()) {
            if (ProxySharingDispatcher.supportSpec(proxySpec)) {
                ISeatStore seatStore = storeFactory.createSeatStore(proxySpec.getId());
                IDelegateProxyStore delegateProxyStore = storeFactory.createDelegateProxyStore(proxySpec.getId());

                ProxySharingScaler proxySharingScaler = new ProxySharingScaler(seatStore, proxySpec, delegateProxyStore);
                createBean(proxySharingScaler, "proxySharingScaler_" + proxySpec.getId());

                ProxySharingDispatcher proxySharingDispatcher = new ProxySharingDispatcher(proxySpec, delegateProxyStore, seatStore);
                createBean(proxySharingDispatcher, "proxySharingDispatcher_" + proxySpec.getId());

                dispatchers.put(proxySpec.getId(), proxySharingDispatcher);
            } else {
                dispatchers.put(proxySpec.getId(), defaultProxyDispatcher);
            }
        }
    }

    public IProxyDispatcher getDispatcher(String specId) {
        return dispatchers.get(specId);
    }

    private <T> void createBean(T bean, String beanName) {
        beanFactory.autowireBean(bean);
        Object initializedBean = beanFactory.initializeBean(bean, beanName);
        beanFactory.registerSingleton(beanName, initializedBean);
    }

}
