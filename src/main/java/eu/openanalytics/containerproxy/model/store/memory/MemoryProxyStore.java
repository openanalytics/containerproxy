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
package eu.openanalytics.containerproxy.model.store.memory;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryProxyStore implements IProxyStore {

    private final ConcurrentHashMap<String, Proxy> activeProxies = new ConcurrentHashMap<>();

    @Override
    public Collection<Proxy> getAllProxies() {
        return Collections.unmodifiableCollection(activeProxies.values());
    }

    @Override
    public void addProxy(Proxy proxy) {
        activeProxies.put(proxy.getId(), proxy);
    }

    @Override
    public void removeProxy(Proxy proxy) {
        activeProxies.remove(proxy.getId());
    }

    @Override
    public void updateProxy(Proxy proxy) {
        activeProxies.put(proxy.getId(), proxy);
    }

    @Override
    public Proxy getProxy(String proxyId) {
        return activeProxies.get(proxyId);
    }

}
