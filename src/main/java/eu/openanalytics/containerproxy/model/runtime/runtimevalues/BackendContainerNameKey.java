/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
package eu.openanalytics.containerproxy.model.runtime.runtimevalues;

public class BackendContainerNameKey extends RuntimeValueKey<String> {

    private BackendContainerNameKey() {
        super("openanalytics.eu/sp-backend-container-name",
                "SHINYPROXY_BACKEND_CONTAINER_NAME",
                false,
                false,
                false,
                false, // important: may not be exposed in API for security
                false,
                true,
                String.class);
    }

    public static BackendContainerNameKey inst = new BackendContainerNameKey();

    @Override
    public String deserializeFromString(String value) {
        return value;
    }

    @Override
    public String serializeToString(String value) {
        return value;
    }

}
