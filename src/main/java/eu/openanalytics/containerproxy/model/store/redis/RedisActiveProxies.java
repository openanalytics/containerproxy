/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.containerproxy.model.store.redis;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IActiveProxies;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedisActiveProxies implements IActiveProxies {

    @Inject
    private RedisTemplate<String, Proxy> redisTemplate;

    @Inject
    private ProxyMappingManager mappingManager;

    @Inject
    private IdentifierService identifierService;

    private final Logger logger = LogManager.getLogger(RedisActiveProxies.class);

    private String redisKeyPrefix;

    private HashOperations<String, String, Proxy> ops;

    private final ConcurrentHashMap<String, Map<String, URI>> targetsCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        redisKeyPrefix = "shinyproxy_" + identifierService.realmId + "__";
        ops = redisTemplate.opsForHash();
    }

    @Override
    public List<Proxy> getAllProxies() {
        List<Proxy> res = ops.values(redisKeyPrefix + "active_proxies");
        res.forEach(this::cacheProxy);
        return res;
    }

    @Override
    public void addProxy(Proxy proxy) {
        logger.info("Add proxy {}", proxy.getId());
        ops.put(redisKeyPrefix + "active_proxies", proxy.getId(), proxy);
        cacheProxy(proxy);
    }

    @Override
    public void removeProxy(Proxy proxy) {
        logger.info("Remove proxy {}", proxy.getId());
        ops.delete(redisKeyPrefix + "active_proxies", proxy.getId());
        // TODO remove from cache
    }

    @Override
    public void update(Proxy proxy) {
        logger.info("Update proxy {}", proxy.getId());
        ops.put(redisKeyPrefix + "active_proxies", proxy.getId(), proxy);
        cacheProxy(proxy);
    }

    @Override
    public Proxy getProxy(String proxyId) {
        // TODO maybe use a cache for this (only for Up Proxies), first check how much this is used
        Proxy proxy = ops.get(redisKeyPrefix + "active_proxies", proxyId);
        if (proxy != null) {
            cacheProxy(proxy);
        }
        return proxy;
    }

    public void cacheProxy(Proxy proxy) {
        Map<String, URI> newTargets = proxy.getTargets();
        Map<String, URI> oldTargets = targetsCache.put(proxy.getId(), newTargets);

        if (oldTargets == null || oldTargets != newTargets) {
            for (Map.Entry<String, URI> target : newTargets.entrySet()) {
                mappingManager.addMapping(proxy.getId(), target.getKey(), target.getValue());
            }
        }
    }

}
