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
package eu.openanalytics.containerproxy.model.runtime.runtimevalues;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class BackendContainerName {

    String value;
    String name;
    String namespace;

    @JsonCreator
    public BackendContainerName(String value) {
        this.value = value;
        if (value.contains("/")) {
            String[] split = value.split("/", 2);
            namespace = split[0];
            name = split[1];
        } else {
            namespace = "default";
            name = value;
        }
    }

    public BackendContainerName(String namespace, String name) {
        this.value = namespace + "/" + name;
        this.namespace = namespace;
        this.name = name;
    }

    @Override
    public String toString() {
        return value;
    }

    public String getNamespace()  {
        return namespace;
    }

    public String getName()  {
        return name;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

}
