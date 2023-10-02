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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.model.runtime.ParameterNames;

public class ParameterNamesKey extends RuntimeValueKey<ParameterNames> {

    public ParameterNamesKey() {
        super("openanalytics.eu/sp-parameters-names",
                "SHINYPROXY_PARAMETER_NAMES",
                false,
                true,
                false,
                true, // IMPORTANT: only the names of the values may be exposed through the API
                false,
                false,
                ParameterNames.class);
    }

    public static ParameterNamesKey inst = new ParameterNamesKey();


    @Override
    public ParameterNames deserializeFromString(String value) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(value, ParameterNames.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String serializeToString(ParameterNames value) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
