/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.memory;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryDelegateProxyStore implements IDelegateProxyStore {

    private final ConcurrentHashMap<String, DelegateProxy> delegateProxies = new ConcurrentHashMap<>();

    @Override
    public Collection<DelegateProxy> getAllDelegateProxies() {
        return Collections.unmodifiableCollection(delegateProxies.values());
    }

    @Override
    public void addDelegateProxy(DelegateProxy delegateProxy) {
        delegateProxies.put(delegateProxy.getProxy().getId(), delegateProxy);
    }

    @Override
    public void removeDelegateProxy(String delegateProxyId) {
        delegateProxies.remove(delegateProxyId);
    }

    @Override
    public void updateDelegateProxy(DelegateProxy delegateProxy) {
        delegateProxies.put(delegateProxy.getProxy().getId(), delegateProxy);
    }

    @Override
    public DelegateProxy getDelegateProxy(String delegateProxyId) {
        return delegateProxies.get(delegateProxyId);
    }

}
