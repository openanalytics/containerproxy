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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.model.Views;

import java.util.List;

@JsonView(Views.Default.class)
public class ParameterNames {
    private final List<ParameterName> values;

    @JsonCreator
    public ParameterNames(List<ParameterName> values) {
        this.values = values;
    }

    @JsonValue
    public List<ParameterName> jsonValue() {
        return values;
    }

    public List<ParameterName> getParametersNames() {
        return values;
    }

    @JsonView(Views.Default.class)
    public static class ParameterName {

        private final String displayName;
        private final String description;
        private final String value;

        @JsonCreator
        public ParameterName(@JsonProperty("displayName") String displayName,
                             @JsonProperty("description") String description,
                             @JsonProperty("value") String value) {
            this.displayName = displayName;
            this.description = description;
            this.value = value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public String getValue() {
            return value;
        }

    }

}
