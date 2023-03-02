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
package eu.openanalytics.containerproxy.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProxyHashMap implements ICleanupStoppedProxies {

    private static final List<ConcurrentHashMap<String, ?>> maps = new ArrayList<>();

    public static synchronized <V> ConcurrentHashMap<String, V> create() {
        ConcurrentHashMap<String, V> map = new ConcurrentHashMap<>();
        maps.add(map);
        return map;
    }

    @Override
    public void cleanupProxy(String proxyId) {
        maps.forEach(map -> map.remove(proxyId));
    }

}
