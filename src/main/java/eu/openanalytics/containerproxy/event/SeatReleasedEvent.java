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

import eu.openanalytics.containerproxy.model.runtime.ProxyStopReason;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class SeatReleasedEvent extends BridgeableEvent {

    @With
    String source;

    String specId;

    String claimingProxyId;

    String seatId;

    ProxyStopReason proxyStopReason;

    public SeatReleasedEvent(String specId, String seatId, String claimingProxyId, ProxyStopReason proxyStopReason) {
        source = "SOURCE_NOT_AVAILABLE";
        this.specId = specId;
        this.seatId = seatId;
        this.claimingProxyId = claimingProxyId;
        this.proxyStopReason = proxyStopReason;
    }

}
