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
package eu.openanalytics.containerproxy.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class PendingProxyEvent extends BridgeableEvent {

    String specId;

    String proxyId;

    @JsonCreator
    public PendingProxyEvent(@JsonProperty("source") String source,
                             @JsonProperty("specId") String specId,
                             @JsonProperty("proxyId") String proxyId) {
        super(source);
        this.specId = specId;
        this.proxyId = proxyId;
    }

    public PendingProxyEvent(String specId, String proxyId) {
        this(SOURCE_NOT_AVAILABLE, specId, proxyId);
    }

    @Override
    public PendingProxyEvent withSource(String source) {
        return new PendingProxyEvent(source, specId, proxyId);
    }

}
