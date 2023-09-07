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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import eu.openanalytics.containerproxy.model.runtime.Proxy;

import java.util.Set;

public class DelegateProxy {

    private final Proxy proxy;
    private final Set<String> seatIds;
    private final DelegateProxyStatus delegateProxyStatus;

    @JsonCreator
    public DelegateProxy(@JsonProperty("proxy") Proxy proxy,
                         @JsonProperty("seatIds") Set<String> seatIds,
                         @JsonProperty("delegateProxyStatus") DelegateProxyStatus delegateProxyStatus) {
        this.proxy = proxy;
        this.seatIds = seatIds;
        this.delegateProxyStatus = delegateProxyStatus;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public Set<String> getSeatIds() {
        return seatIds;
    }

    public DelegateProxyStatus getDelegateProxyStatus() {
        return delegateProxyStatus;
    }

}
