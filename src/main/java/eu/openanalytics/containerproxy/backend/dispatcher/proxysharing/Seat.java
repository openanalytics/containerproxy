/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class Seat {

    private final String id;
    private final String delegateProxyId;
    private String delegatingProxyId;

    public Seat(String delegateProxyId) {
        this.delegateProxyId = delegateProxyId;
        id = UUID.randomUUID().toString();
    }

    @JsonCreator
    public Seat(@JsonProperty("id") String id,
                @JsonProperty("delegateProxyId") String delegateProxyId,
                @JsonProperty("delegatingProxyId") String delegatingProxyId
    ) {
        this.delegateProxyId = delegateProxyId;
        this.id = id;
        this.delegatingProxyId = delegatingProxyId;
    }

    public void claim(String proxyId) {
        if (delegatingProxyId != null) {
            throw new IllegalStateException(String.format("Seat %s already claimed by %s", id, proxyId));
        }
        delegatingProxyId = proxyId;
    }

    public void release() {
        if (delegatingProxyId == null) {
            throw new IllegalStateException(String.format("Seat %s not claimed", id));
        }
        delegatingProxyId = null;
    }

}
