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
import eu.openanalytics.containerproxy.model.runtime.ProxyStopReason;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class SeatReleasedEvent extends BridgeableEvent {

    String specId;

    String claimingProxyId;

    String seatId;

    ProxyStopReason proxyStopReason;

    @JsonCreator
    public SeatReleasedEvent(@JsonProperty("source") String source,
                             @JsonProperty("specId") String specId,
                             @JsonProperty("seatId") String seatId,
                             @JsonProperty("claimingProxyId") String claimingProxyId,
                             @JsonProperty("proxyStopReason") ProxyStopReason proxyStopReason) {
        super(source);
        this.specId = specId;
        this.seatId = seatId;
        this.claimingProxyId = claimingProxyId;
        this.proxyStopReason = proxyStopReason;
    }

    public SeatReleasedEvent(String specId, String seatId, String claimingProxyId, ProxyStopReason proxyStopReason) {
        this(SOURCE_NOT_AVAILABLE, specId, seatId, claimingProxyId, proxyStopReason);
    }

    @Override
    public SeatReleasedEvent withSource(String source) {
        return new SeatReleasedEvent(source, specId, seatId, claimingProxyId, proxyStopReason);
    }

}
