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
package eu.openanalytics.containerproxy.service.portallocator.redis;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.portallocator.IPortAllocator;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RedisPortAllocator implements IPortAllocator {

    private final String portOwnersKey;

    private final RedisTemplate<String, PortList> portListRedisTemplate;

    public RedisPortAllocator(RedisTemplate<String, PortList> portListRedisTemplate,
                              IdentifierService identifierService) {
        this.portListRedisTemplate = portListRedisTemplate;
        portOwnersKey = "shinyproxy_" + identifierService.realmId + "__ports";
    }

    @Override
    public Integer allocate(int rangeFrom, int rangeTo, String ownerId) {
        return portListRedisTemplate.execute(new SessionCallback<Integer>() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public Integer execute(@Nonnull RedisOperations operations) throws DataAccessException {
                int nextPort;
                do {
                    HashOperations ops = operations.opsForHash();

                    operations.watch(portOwnersKey);

                    // 1. get all currently allocated ports
                    Map<String, PortList> entries = ops.entries(portOwnersKey);
                    Set<Integer> allocated = entries.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

                    // 2. find first available port
                    nextPort = rangeFrom;
                    while (allocated.contains(nextPort)) nextPort++;

                    if (rangeTo > 0 && nextPort > rangeTo) {
                        throw new ContainerProxyException("Cannot create container: all allocated ports are currently in use. Please try again later or contact an administrator.");
                    }

                    // 3. save this port under ownerId
                    PortList ownedPorts = entries.getOrDefault(ownerId, new PortList());
                    ownedPorts.add(nextPort);

                    operations.multi();
                    ops.put(portOwnersKey, ownerId, ownedPorts);
                } while (operations.exec().isEmpty());
                return nextPort;
            }
        });
    }

    @Override
    public void addExistingPort(String ownerId, int port) {
        throw new IllegalStateException("Redis store does not support allocating existing ports.");
    }

    @Override
    public void release(String ownerId) {
        HashOperations<String, String, PortList> ops = portListRedisTemplate.opsForHash();
        ops.delete(portOwnersKey, ownerId);
    }

    @Override
    public Set<Integer> getOwnedPorts(String ownerId) {
        HashOperations<String, PortList, PortList> ops = portListRedisTemplate.opsForHash();
        List<Integer> res = ops.get(portOwnersKey, ownerId);
        if (res == null) {
            return new HashSet<>();
        }
        return new HashSet<>(res);
    }

    public static class PortList extends ArrayList<Integer> {
    }

}
