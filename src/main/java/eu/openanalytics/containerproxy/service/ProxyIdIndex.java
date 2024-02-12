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
package eu.openanalytics.containerproxy.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public abstract class ProxyIdIndex<T> {

    private final LoadingCache<T, String> cache;
    private final IProxyStore proxyStore;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ProxyIdIndex(IProxyStore proxyStore, Filter<T> filter) {
        this.proxyStore = proxyStore;
        cache = Caffeine.newBuilder()
            .scheduler(Scheduler.systemScheduler())
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(key -> proxyStore.getAllProxies()
                .stream()
                .filter(proxy -> filter.filter(key, proxy))
                .findFirst()
                .map(Proxy::getId).orElse(null));
    }

    protected Proxy getProxy(String userId, T key) {
        String proxyId = cache.get(key);
        if (proxyId == null) {
            // no result found (even when using the lookup function)
            return null;
        }
        Proxy proxy = proxyStore.getProxy(proxyId);
        if (proxy == null) {
            // the proxyId from the cache no longer exists -> force the cache to look for a new proxy
            try {
                // try to refresh the proxy
                proxyId = cache.refresh(key).get();
                if (proxyId == null) {
                    return null;
                }
                proxy = proxyStore.getProxy(proxyId);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        if (isInvalidProxy(proxy, proxyId, userId)) {
            return null;
        }

        return proxy;
    }

    /**
     * Checks ownership and proxyId of the proxy as additional defense, but this should never happen.
     * If this happens, the cache pointed to a wrong proxy.
     *
     * @param proxy   the proxy to validate
     * @param proxyId the expected proxyId (i.e. what was in the cache)
     * @param userId  the expected userId (i.e. who is trying to retrieve a proxy)
     * @return whether the proxy is invalid
     */
    private boolean isInvalidProxy(Proxy proxy, String proxyId, String userId) {
        if (proxy == null) {
            return false;
        }
        if (!proxy.getId().equals(proxyId)) {
            logger.warn("Invalid proxy state, proxyId: {}, does not match expected: {}", proxy.getId(), proxyId);
            return true;
        }
        if (!proxy.getUserId().equals(userId)) {
            logger.warn("Invalid proxy state for proxyId: {}, userId: {} does not match expected: {}", proxyId, proxy.getUserId(), userId);
            return true;
        }

        return false;
    }

    @FunctionalInterface
    public interface Filter<T> {
        boolean filter(T key, Proxy proxy);
    }

}
