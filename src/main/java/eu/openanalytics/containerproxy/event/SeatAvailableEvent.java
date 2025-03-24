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
package eu.openanalytics.containerproxy.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class SeatAvailableEvent extends BridgeableEvent {

    String specId;

    String intendedProxyId;

    @JsonCreator
    public SeatAvailableEvent(@JsonProperty("source") String source,
                              @JsonProperty("specId") String specId,
                              @JsonProperty("claimingProxyId") String claimingProxyId) {
        super(source);
        this.specId = specId;
        this.intendedProxyId = claimingProxyId;
    }

    public SeatAvailableEvent(String specId, String claimingProxyId) {
        this(SOURCE_NOT_AVAILABLE, specId, claimingProxyId);
    }

    @Override
    public SeatAvailableEvent withSource(String source) {
        return new SeatAvailableEvent(source, specId, intendedProxyId);
    }

}
