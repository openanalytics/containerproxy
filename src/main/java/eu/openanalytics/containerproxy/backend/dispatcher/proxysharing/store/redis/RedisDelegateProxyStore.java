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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.redis;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import org.springframework.data.redis.core.BoundHashOperations;

import java.util.Collection;

public class RedisDelegateProxyStore implements IDelegateProxyStore {

    private final BoundHashOperations<String, String, DelegateProxy> delegateProxyOps;

    public RedisDelegateProxyStore(BoundHashOperations<String, String, DelegateProxy> delegateProxyOps) {
        this.delegateProxyOps = delegateProxyOps;
    }

    @Override
    public Collection<DelegateProxy> getAllDelegateProxies() {
        return delegateProxyOps.values();
    }

    @Override
    public void addDelegateProxy(DelegateProxy delegateProxy) {
        delegateProxyOps.put(delegateProxy.getProxy().getId(), delegateProxy);
    }

    @Override
    public void removeDelegateProxy(String delegateProxyId) {
        delegateProxyOps.delete(delegateProxyId);
    }

    @Override
    public void updateDelegateProxy(DelegateProxy delegateProxy) {
        delegateProxyOps.put(delegateProxy.getProxy().getId(), delegateProxy);
    }

    @Override
    public DelegateProxy getDelegateProxy(String delegateProxyId) {
        return delegateProxyOps.get(delegateProxyId);
    }

}
