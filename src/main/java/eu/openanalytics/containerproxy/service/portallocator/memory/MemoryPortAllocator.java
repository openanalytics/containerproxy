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
package eu.openanalytics.containerproxy.service.portallocator.memory;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.service.portallocator.IPortAllocator;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MemoryPortAllocator implements IPortAllocator {

    private final HashMap<String, HashSet<Integer>> ports = new HashMap<>();

    @Override
    public synchronized Integer allocate(int rangeFrom, int rangeTo, String ownerId) {
        Set<Integer> allocated = ports.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        int nextPort = rangeFrom;
        while (allocated.contains(nextPort)) nextPort++;
        if (rangeTo > 0 && nextPort > rangeTo) {
            throw new ContainerProxyException("Cannot create container: all allocated ports are currently in use. Please try again later or contact an administrator.");
        }
        ports.putIfAbsent(ownerId, new HashSet<>());
        ports.get(ownerId).add(nextPort);
        return nextPort;
    }

    @Override
    public synchronized void addExistingPort(String ownerId, int port) {
        ports.putIfAbsent(ownerId, new HashSet<>());
        ports.get(ownerId).add(port);
    }

    @Override
    public synchronized void release(String ownerId) {
        ports.remove(ownerId);
    }

    @Override
    public synchronized Set<Integer> getOwnedPorts(String ownerId) {
        return new HashSet<>(ports.getOrDefault(ownerId, new HashSet<>()));
    }

}
