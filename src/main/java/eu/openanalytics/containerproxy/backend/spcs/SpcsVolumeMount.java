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
package eu.openanalytics.containerproxy.backend.spcs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a volume mount for an SPCS container.
 * 
 * References a volume by name and specifies the mount path in the container.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpcsVolumeMount {
    
    /**
     * The name of the volume to mount (must match a volume name in the service spec).
     */
    private String name;
    
    /**
     * The path where the volume should be mounted in the container.
     */
    private String mountPath;
}
