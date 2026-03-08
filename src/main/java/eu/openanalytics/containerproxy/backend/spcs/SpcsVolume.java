/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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

import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a volume definition for SPCS service specification.
 * 
 * Volumes are defined at the service level and can be shared between containers.
 * 
 * Reference: https://docs.snowflake.com/en/developer-guide/snowpark-container-services/specification-reference
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpcsVolume {
    
    /**
     * The name of the volume (referenced in volumeMounts).
     */
    private String name;
    
    /**
     * Volume source type: local, stage, memory, or block
     */
    private String source;
    
    /**
     * Size in bytes (required for memory and block volumes).
     * Supports units: K, Ki, M, Mi, G, Gi, etc.
     */
    private String size;
    
    /**
     * UID (User ID) for stage volumes (optional).
     */
    private Integer uid;
    
    /**
     * GID (Group ID) for stage volumes (optional).
     */
    private Integer gid;
    
    /**
     * Block storage configuration (optional, for block volumes).
     */
    private SpcsBlockConfig blockConfig;
    
    /**
     * Stage configuration (optional, for stage volumes).
     */
    private SpcsStageConfig stageConfig;

    public SpcsVolume resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
            .name(name == null ? null : resolver.evaluateToString(name, context))
            .source(source == null ? null : resolver.evaluateToString(source, context))
            .size(size == null ? null : resolver.evaluateToString(size, context))
            .uid(uid == null ? null : resolver.evaluateToInteger(String.valueOf(uid), context))
            .gid(gid == null ? null : resolver.evaluateToInteger(String.valueOf(gid), context))
            .blockConfig(blockConfig == null ? null : blockConfig.resolve(resolver, context))
            .stageConfig(stageConfig == null ? null : stageConfig.resolve(resolver, context))
            .build();
    }
}
