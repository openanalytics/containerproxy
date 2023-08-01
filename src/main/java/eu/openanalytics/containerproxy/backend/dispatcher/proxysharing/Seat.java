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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing;

import lombok.Data;

import java.util.UUID;

@Data
public class Seat {

    private final String targetId;

    private final String id;

    private String claimingProxyId;

    public Seat(String targetId) {
        this.targetId = targetId;
        id = UUID.randomUUID().toString();
    }

    public void claim(String proxyId) {
        if (claimingProxyId != null) {
            throw new IllegalStateException(String.format("Seat %s already claimed by %s", id, proxyId));
        }
        claimingProxyId = proxyId;
    }

    public void release() {
        if (claimingProxyId == null) {
            throw new IllegalStateException(String.format("Seat %s not claimed", id));
        }
        claimingProxyId = null;
    }

}
