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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.HashSet;
import java.util.Set;

@Value
@EqualsAndHashCode
@Builder(toBuilder = true)
@AllArgsConstructor
@JsonView(Views.Default.class)
public class DelegateProxy {

    Proxy proxy;
    Set<String> seatIds;
    DelegateProxyStatus delegateProxyStatus;
    // store hash of spec, so we can compare versions
    String proxySpecHash;

    @JsonCreator
    public static DelegateProxy createDelegateProxy(@JsonProperty("proxy") Proxy proxy,
                                                    @JsonProperty("seatIds") Set<String> seatIds,
                                                    @JsonProperty("delegateProxyStatus") DelegateProxyStatus delegateProxyStatus,
                                                    @JsonProperty("proxySpecHash") String proxySpecHash) {

        return DelegateProxy.builder()
            .proxy(proxy)
            .seatIds(seatIds)
            .delegateProxyStatus(delegateProxyStatus)
            .proxySpecHash(proxySpecHash)
            .build();
    }

    public static class DelegateProxyBuilder {
        public DelegateProxyBuilder removeSeatId(String seatId) {
            Set<String> seatIds = new HashSet<>(this.seatIds);
            seatIds.remove(seatId);
            this.seatIds(seatIds);
            return this;
        }

        public DelegateProxyBuilder addSeatId(String seatId) {
            Set<String> seatIds = new HashSet<>(this.seatIds);
            seatIds.add(seatId);
            this.seatIds(seatIds);
            return this;
        }
    }

}
