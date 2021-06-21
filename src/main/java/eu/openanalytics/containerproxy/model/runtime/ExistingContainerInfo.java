/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.containerproxy.model.runtime;

import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;

import java.util.Map;

/**
 * Provides information about existing containers for the App Recovery feature.
 */
public class ExistingContainerInfo {

    public ExistingContainerInfo(String containerId,
                                 Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues,
                                 String image,
                                 Map<Integer, Integer> portBindings,
                                 Map<String, Object> parameters
    ) {
        this.containerId = containerId;
        this.runtimeValues = runtimeValues;
        this.image = image;
        this.portBindings = portBindings;
        this.parameters = parameters;

    }

    private final String containerId;
    private final Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues;
    private final String image;
    private final Map<Integer, Integer> portBindings;
    private final Map<String, Object> parameters;

    public String getContainerId() {
        return containerId;
    }

    public Map<RuntimeValueKey<?>, RuntimeValue> getRuntimeValues() {
        return runtimeValues;
    }

    public String getImage() {
        return image;
    }


    public Map<Integer, Integer> getPortBindings() {
        return portBindings;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public RuntimeValue getRuntimeValue(RuntimeValueKey<?> key) {
        return runtimeValues.get(key);
    }

}
