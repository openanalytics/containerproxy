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
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.context.annotation.PropertySource;

import java.time.Duration;

@Value
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ProxyPauseEvent extends BridgeableEvent {

    String proxyId;
    String userId;
    String specId;
    Duration usageTime;

    @JsonCreator
    public ProxyPauseEvent(@JsonProperty("source") String source,
                           @JsonProperty("proxyId") String proxyId,
                           @JsonProperty("userId") String userId,
                           @JsonProperty("specId") String specId,
                           @JsonProperty("usageTime") Duration usageTime) {
        super(source);
        this.proxyId = proxyId;
        this.userId = userId;
        this.specId = specId;
        this.usageTime = usageTime;
    }

    public ProxyPauseEvent(Proxy proxy) {
        this(SOURCE_NOT_AVAILABLE, proxy.getId(), proxy.getUserId(), proxy.getSpecId(),
            proxy.getStartupTimestamp() == 0 ? null : Duration.ofMillis(System.currentTimeMillis() - proxy.getStartupTimestamp()));
    }

    @Override
    public ProxyPauseEvent withSource(String source) {
        return new ProxyPauseEvent(source, proxyId, userId, specId, usageTime);
    }
}
