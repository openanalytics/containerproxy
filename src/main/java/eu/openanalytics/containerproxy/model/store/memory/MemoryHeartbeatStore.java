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

import eu.openanalytics.containerproxy.model.store.IHeartbeatStore;
import eu.openanalytics.containerproxy.util.ProxyHashMap;

import java.util.concurrent.ConcurrentHashMap;

public class MemoryHeartbeatStore implements IHeartbeatStore {

    private final ConcurrentHashMap<String, Long> heartbeats = ProxyHashMap.create();

    @Override
    public void update(String proxyId, Long currentTimeMillis) {
        heartbeats.put(proxyId, currentTimeMillis);
    }

    @Override
    public Long get(String proxyId) {
        return heartbeats.get(proxyId);
    }

}
