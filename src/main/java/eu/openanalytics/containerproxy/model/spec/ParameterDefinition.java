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
package eu.openanalytics.containerproxy.model.spec;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.util.List;

@ConstructorBinding
public class ParameterDefinition {

    private final String id;
    private final String displayName;
    private final String description;

    private final String defaultValue;

    // a mapping of the raw value (used in the backend) to a human friendly name used in the front-end
    private final BidiMap<String, String> valueNames;

    public ParameterDefinition(String id, String displayName, String description, List<ValueName> valueNames, String defaultValue) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.defaultValue = defaultValue;
        this.valueNames = new DualHashBidiMap<>();
        if (valueNames != null) {
            for (ValueName valueName : valueNames) {
                if (this.valueNames.containsKey(valueName.value)) {
                    throw new IllegalStateException(String.format("A ValueName mapping already contains a mapping for value \"%s\"", valueName.value));
                }
                if (this.valueNames.containsValue(valueName.name)) {
                    throw new IllegalStateException(String.format("A ValueName mapping already contains a mapping with the name \"%s\"", valueName.name));
                }
                this.valueNames.put(valueName.value, valueName.name);
            }
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    // used in Thymeleaf template!
    @SuppressWarnings("unused")
    public String getDisplayNameOrId() {
        if (displayName != null) {
            return displayName;
        }
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getId() {
        return id;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Given the (backend) value, return the human friendly name for the value.
     * @param value the backend-value
     * @return the human friendly name of the value
     */
    public String getNameOfValue(String value) {
        return valueNames.getOrDefault(value, value);
    }

    public boolean hasNameForValue(String value) {
        return valueNames.containsKey(value);
    }

    /**
     * Given the (human friendly name), return the backend value
     * @param name the human-friendly name
     * @return the backend value
     */
    public String getValueForName(String name) {
        return valueNames.getKey(name);
    }

    @ConstructorBinding
    public static class ValueName {

        private final String name;
        private final String value;

        public ValueName(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

}
