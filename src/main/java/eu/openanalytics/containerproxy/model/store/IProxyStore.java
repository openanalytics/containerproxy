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
package eu.openanalytics.containerproxy.model.store;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

import java.util.Collection;
import java.util.List;

/**
 * Interface to manage Active Proxies in the ProxyService.
 */
public interface IProxyStore {

    Collection<Proxy> getAllProxies();

    void addProxy(Proxy proxy);

    void removeProxy(Proxy proxy);

    void updateProxy(Proxy proxy);

    Proxy getProxy(String proxyId);

    List<Proxy> getUserProxies(String userId);
}
