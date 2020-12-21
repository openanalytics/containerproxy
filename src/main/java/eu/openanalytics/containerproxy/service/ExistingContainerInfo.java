/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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
package eu.openanalytics.containerproxy.service;

import java.util.Map;

public class ExistingContainerInfo {

    public ExistingContainerInfo(String containerId,
                                 String proxyId,
                                 String proxySpecId,
                                 String image,
                                 String userId,
                                 Map<Integer, Integer> portBindings,
                                 long startupTimestamp,
                                 Map<String, Object> parameters
    ) {
        this.containerId = containerId;
        this.proxyId = proxyId;
        this.proxySpecId = proxySpecId;
        this.image = image;
        this.userId = userId;
        this.portBindings = portBindings;
        this.startupTimestamp = startupTimestamp;
        this.parameters = parameters;
    }

    private final String containerId;
    private final String proxyId;
    private final String proxySpecId;
    private final String image;
    private final String userId;
    private final Map<Integer, Integer> portBindings;
    private final long startupTimestamp;
    private final Map<String, Object> parameters;

    public String getContainerId() {
        return containerId;
    }

    public String getProxyId() {
        return proxyId;
    }

    public String getProxySpecId() {
        return proxySpecId;
    }

    public String getImage() {
        return image;
    }

    public String getUserId() {
        return userId;
    }

    public Map<Integer, Integer> getPortBindings() {
        return portBindings;
    }

    public long getStartupTimestamp() {
        return startupTimestamp;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    // TODO copy?
}