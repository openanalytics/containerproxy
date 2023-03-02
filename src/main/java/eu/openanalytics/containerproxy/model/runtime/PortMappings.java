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

import java.util.ArrayList;
import java.util.List;

public class PortMappings {

    private final List<PortMappingEntry> portMappings;

    @JsonCreator
    public PortMappings(List<PortMappingEntry> portMappings) {
        this.portMappings = portMappings;
    }

    public PortMappings() {
        portMappings = new ArrayList<>();
    }

    public List<PortMappingEntry> getPortMappings() {
        return portMappings;
    }

    public void addPortMapping(PortMappingEntry portMappingEntry) {
        this.portMappings.add(portMappingEntry);
    }

    @JsonValue
    public List<PortMappingEntry> jsonValue() {
        return portMappings;
    }

    public static class PortMappingEntry {
        private final String name;

        private final Integer port;

        private final String targetPath;

        @JsonCreator
        public PortMappingEntry(@JsonProperty("name") String name,
                                @JsonProperty("port") Integer port,
                                @JsonProperty("targetPath") String targetPath) {
            this.name = name;
            this.port = port;
            this.targetPath = targetPath;
        }

        public String getName() {
            return name;
        }

        public Integer getPort() {
            return port;
        }

        public String getTargetPath() {
            return targetPath;
        }

    }

}
