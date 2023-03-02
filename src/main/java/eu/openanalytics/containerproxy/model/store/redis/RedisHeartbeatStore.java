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

import eu.openanalytics.containerproxy.model.store.IHeartbeatStore;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.util.ICleanupStoppedProxies;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

public class RedisHeartbeatStore implements IHeartbeatStore, ICleanupStoppedProxies {

    @Inject
    private RedisTemplate<String, Long> redisTemplate;

    @Inject
    private IdentifierService identifierService;

    private String redisKey;

    private HashOperations<String, String, Long> ops;

    @PostConstruct
    public void init() {
        redisKey = "shinyproxy_" + identifierService.realmId + "__heartbeats";
        ops = redisTemplate.opsForHash();
    }

    @Override
    public void update(String proxyId, Long currentTimeMillis) {
        ops.put(redisKey, proxyId, currentTimeMillis);
    }

    @Override
    public Long get(String proxyId) {
        return ops.get(redisKey, proxyId);
    }

    @Override
    public void cleanupProxy(String proxyId) {
        ops.delete(redisKey, proxyId);
    }

}
