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
package eu.openanalytics.containerproxy.model.store.redis;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
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

public class RedisProxyStore implements IProxyStore {

    @Inject
    private RedisTemplate<String, Proxy> redisTemplate;

    @Inject
    private ProxyMappingManager mappingManager;

    @Inject
    private IdentifierService identifierService;

    private final Logger logger = LogManager.getLogger(RedisProxyStore.class);

    private String redisKey;

    private HashOperations<String, String, Proxy> ops;

    private final ConcurrentHashMap<String, Map<String, URI>> targetsCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        redisKey = "shinyproxy_" + identifierService.realmId + "__active_proxies";
        ops = redisTemplate.opsForHash();
    }

    @Override
    public List<Proxy> getAllProxies() {
        List<Proxy> res = ops.values(redisKey);
        res.forEach(this::updateMappings);
        return res;
    }

    @Override
    public void addProxy(Proxy proxy) {
        logger.debug("Add proxy {}", proxy.getId());
        ops.put(redisKey, proxy.getId(), proxy);
        updateMappings(proxy);
    }

    @Override
    public void removeProxy(Proxy proxy) {
        logger.debug("Remove proxy {}", proxy.getId());
        ops.delete(redisKey, proxy.getId());
        updateMappings(proxy);
    }

    @Override
    public void updateProxy(Proxy proxy) {
        logger.debug("Update proxy {}", proxy.getId());
        ops.put(redisKey, proxy.getId(), proxy);
        updateMappings(proxy);
    }

    @Override
    public Proxy getProxy(String proxyId) {
        // TODO maybe use a cache for this (only for Up Proxies), first check how much this is used
        Proxy proxy = ops.get(redisKey, proxyId);
        if (proxy != null) {
            updateMappings(proxy);
        }
        return proxy;
    }

    public void updateMappings(Proxy proxy) {
        if (proxy.getStatus().isUnavailable()) {
            Map<String, URI> oldTargets = targetsCache.remove(proxy.getId());
            if (oldTargets != null) {
                // there were still some old mappings -> remove them
                logger.debug("Redis: remove mappings for {}", proxy.getId());
                for (Map.Entry<String, URI> target : proxy.getTargets().entrySet()) {
                    mappingManager.removeMapping(target.getKey());
                }
            }
            return;
        }

        Map<String, URI> newTargets = proxy.getTargets();
        Map<String, URI> oldTargets = targetsCache.put(proxy.getId(), newTargets);

        if (oldTargets == null || oldTargets != newTargets) {
            for (Map.Entry<String, URI> target : newTargets.entrySet()) {
                mappingManager.addMapping(proxy.getId(), target.getKey(), target.getValue());
            }
        }
    }

}
