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
package eu.openanalytics.containerproxy.model.store.memory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import eu.openanalytics.containerproxy.service.AccessControlEvaluationService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryProxyStore implements IProxyStore {

    private final ConcurrentHashMap<String, Proxy> activeProxies = new ConcurrentHashMap<>();
    private final ListMultimap<String, String> userProxies = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    private final AccessControlEvaluationService accessControlEvaluationService;

    public MemoryProxyStore(AccessControlEvaluationService accessControlEvaluationService) {
        this.accessControlEvaluationService = accessControlEvaluationService;
    }

    @Override
    public Collection<Proxy> getAllProxies() {
        return Collections.unmodifiableCollection(activeProxies.values());
    }

    @Override
    public void addProxy(Proxy proxy) {
        activeProxies.put(proxy.getId(), proxy);
        userProxies.put(proxy.getUserId(), proxy.getId());
    }

    @Override
    public void removeProxy(Proxy proxy) {
        activeProxies.remove(proxy.getId());
        userProxies.remove(proxy.getUserId(), proxy.getId());
    }

    @Override
    public void updateProxy(Proxy proxy) {
        activeProxies.put(proxy.getId(), proxy);
    }

    @Override
    public Proxy getProxy(String proxyId) {
        return activeProxies.get(proxyId);
    }

    @Override
    public List<Proxy> getUserProxies(String userId) {
        List<Proxy> result = new ArrayList<>();
        List<String> ids = userProxies.get(userId);
        for (String proxyId : ids) {
            Proxy proxy = activeProxies.get(proxyId);
            if (proxy != null && accessControlEvaluationService.usernameEquals(proxy.getUserId(), userId)) {
                result.add(proxy);
            }
        }
        return result;
    }

}
