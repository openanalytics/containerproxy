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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStopReason;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.security.core.Authentication;

import java.time.Duration;

@Value
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ProxyStopEvent extends BridgeableEvent {

    String proxyId;
    String userId;
    String specId;
    ProxyStopReason proxyStopReason;
    Duration usageTime;
    @JsonIgnore
    Authentication authentication;

    @JsonCreator
    public ProxyStopEvent(@JsonProperty("source") String source,
                          @JsonProperty("proxyId") String proxyId,
                          @JsonProperty("userId") String userId,
                          @JsonProperty("specId") String specId,
                          @JsonProperty("proxyStopReason") ProxyStopReason proxyStopReason,
                          @JsonProperty("usageTime") Duration usageTime) {
        this(source, proxyId, userId, specId, proxyStopReason, usageTime, null);
    }

    public ProxyStopEvent(String source,
                          String proxyId,
                          String userId,
                          String specId,
                          ProxyStopReason proxyStopReason,
                          Duration usageTime,
                          Authentication authentication) {
        super(source);
        this.proxyId = proxyId;
        this.userId = userId;
        this.specId = specId;
        this.proxyStopReason = proxyStopReason;
        this.usageTime = usageTime;
        this.authentication = authentication;
    }

    public ProxyStopEvent(Proxy proxy, ProxyStopReason proxyStopReason, Authentication authentication) {
        this(SOURCE_NOT_AVAILABLE,
            proxy.getId(),
            proxy.getUserId(),
            proxy.getSpecId(),
            proxyStopReason,
            proxy.getStartupTimestamp() == 0 ? null : Duration.ofMillis(System.currentTimeMillis() - proxy.getStartupTimestamp()),
            authentication);
    }

    @Override
    public ProxyStopEvent withSource(String source) {
        return new ProxyStopEvent(source, proxyId, userId, specId, proxyStopReason, usageTime);
    }

}
