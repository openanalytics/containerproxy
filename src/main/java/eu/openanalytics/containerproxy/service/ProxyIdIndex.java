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
package eu.openanalytics.containerproxy.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IProxyStore;

import java.util.concurrent.TimeUnit;

public abstract class ProxyIdIndex<T> {

    private final LoadingCache<T, String> cache;
    private final IProxyStore proxyStore;

    public ProxyIdIndex(IProxyStore proxyStore, Filter<T> filter) {
        this.proxyStore = proxyStore;
        cache = Caffeine.newBuilder()
            .scheduler(Scheduler.systemScheduler())
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(key -> {
                return proxyStore.getAllProxies()
                    .stream()
                    .filter(proxy -> filter.filter(key, proxy))
                    .findFirst()
                    .map(Proxy::getId).orElse(null);
            });
    }

    protected Proxy getProxy(String userId, T key) {
        String proxyId = cache.get(key);
        if (proxyId == null) {
            return null;
        }
        // check ownership of proxy again as an additional defense
        Proxy proxy = proxyStore.getProxy(proxyId);
        if (!proxy.getUserId().equals(userId)) {
            return null;
        }

        return proxy;
    }

    @FunctionalInterface
    public interface Filter<T> {
        boolean filter(T key, Proxy proxy);
    }

}
