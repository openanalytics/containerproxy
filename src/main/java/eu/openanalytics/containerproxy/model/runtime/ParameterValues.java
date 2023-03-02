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
package eu.openanalytics.containerproxy.model.runtime;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ParameterValues {

    private final Map<String, String> backendValues;
    private final String valueSetName;

    @JsonCreator
    public ParameterValues(@JsonProperty("backendValues") Map<String, String> backendValues, @JsonProperty("valueSetName") String valueSetName) {
        this.backendValues = backendValues;
        this.valueSetName = valueSetName;
    }

    @JsonIgnore
    public int size() {
        return backendValues.size();
    }

    public String getValue(String parameterId) {
        if (!backendValues.containsKey(parameterId)) {
            throw new IllegalArgumentException(String.format("The parameter with id \"%s\" does not exist!", parameterId));
        }
        return backendValues.get(parameterId);
    }

    public Map<String, String> getBackendValues() {
        return backendValues;
    }

    public String getValueSetName() {
        return valueSetName;
    }
}
