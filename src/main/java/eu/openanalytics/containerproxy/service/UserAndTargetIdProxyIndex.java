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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UserAndTargetIdProxyIndex extends ProxyIdIndex<UserAndTargetIdProxyIndex.UserAndTargetIdKey> {

    public UserAndTargetIdProxyIndex(IProxyStore proxyStore) {
        super(proxyStore, (key, proxy) -> Objects.equals(proxy.getTargetId(), key.targetId) && Objects.equals(proxy.getUserId(), key.userId));
        // use Objects.equals because some proxies might not yet be initialized
    }

    public Proxy getProxy(String userId, String targetId) {
        return getProxy(userId, new UserAndTargetIdKey(userId, targetId));
    }

    public record UserAndTargetIdKey(String userId, String targetId) {
    }

}
