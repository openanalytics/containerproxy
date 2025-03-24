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
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerName;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ProxyStartFailedEvent extends BridgeableEvent {

    String proxyId;
    String userId;
    String specId;
    String instance;
    Long createdTimestamp;
    BackendContainerName backendContainerName;

    @JsonCreator
    public ProxyStartFailedEvent(@JsonProperty("source") String source,
                                 @JsonProperty("proxyId") String proxyId,
                                 @JsonProperty("userId") String userId,
                                 @JsonProperty("specId") String specId,
                                 @JsonProperty("instance") String instance,
                                 @JsonProperty("createdTimestamp") long createdTimestamp,
                                 @JsonProperty("backendContainerName") BackendContainerName backendContainerName) {
        super(source);
        this.proxyId = proxyId;
        this.userId = userId;
        this.specId = specId;
        this.instance = instance;
        this.createdTimestamp = createdTimestamp;
        this.backendContainerName = backendContainerName;
    }

    public ProxyStartFailedEvent(Proxy proxy) {
        this(SOURCE_NOT_AVAILABLE,
            proxy.getId(),
            proxy.getUserId(),
            proxy.getSpecId(),
            proxy.getRuntimeValue("SHINYPROXY_APP_INSTANCE"),
            proxy.getCreatedTimestamp(),
            proxy.getContainers().isEmpty() ? null : proxy.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst)
        );
    }

    @Override
    public ProxyStartFailedEvent withSource(String source) {
        return new ProxyStartFailedEvent(source, proxyId, userId, specId, instance, createdTimestamp, backendContainerName);
    }

}
