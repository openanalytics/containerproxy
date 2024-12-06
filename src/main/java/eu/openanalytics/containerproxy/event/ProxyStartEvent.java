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
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerName;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.security.core.Authentication;

@Value
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ProxyStartEvent extends BridgeableEvent {

    String proxyId;
    String userId;
    String specId;
    String instance;
    Long createdTimestamp;
    BackendContainerName backendContainerName;
    ProxyStartupLog proxyStartupLog;
    @JsonIgnore
    Authentication authentication;

    @JsonCreator
    public ProxyStartEvent(@JsonProperty("source") String source,
                           @JsonProperty("proxyId") String proxyId,
                           @JsonProperty("userId") String userId,
                           @JsonProperty("specId") String specId,
                           @JsonProperty("instance") String instance,
                           @JsonProperty("createdTimestamp") Long createdTimestamp,
                           @JsonProperty("backendContainerName") BackendContainerName backendContainerName,
                           @JsonProperty("proxyStartupLog") ProxyStartupLog proxyStartupLog) {
        this(source, proxyId, userId, specId, instance, createdTimestamp, backendContainerName, proxyStartupLog, null);
    }

    public ProxyStartEvent(String source,
                           String proxyId,
                           String userId,
                           String specId,
                           String instance,
                           long createdTimestamp,
                           BackendContainerName backendContainerName,
                           ProxyStartupLog proxyStartupLog,
                           Authentication authentication) {
        super(source);
        this.proxyId = proxyId;
        this.userId = userId;
        this.specId = specId;
        this.instance = instance;
        this.createdTimestamp = createdTimestamp;
        this.backendContainerName = backendContainerName;
        this.proxyStartupLog = proxyStartupLog;
        this.authentication = authentication;
    }

    public ProxyStartEvent(Proxy proxy, ProxyStartupLog proxyStartupLog, Authentication authentication) {
        this(SOURCE_NOT_AVAILABLE,
            proxy.getId(),
            proxy.getUserId(),
            proxy.getSpecId(),
            proxy.getRuntimeValue("SHINYPROXY_APP_INSTANCE"),
            proxy.getCreatedTimestamp(),
            proxy.getContainers().isEmpty() ? null : proxy.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst),
            proxyStartupLog,
            authentication);
    }

    @Override
    public ProxyStartEvent withSource(String source) {
        return new ProxyStartEvent(source, proxyId, userId, specId, instance, createdTimestamp, backendContainerName, proxyStartupLog);
    }

}
