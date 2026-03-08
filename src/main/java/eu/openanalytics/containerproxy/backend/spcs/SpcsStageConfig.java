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

import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for stage volumes in SPCS.
 * 
 * Reference: https://docs.snowflake.com/en/developer-guide/snowpark-container-services/specification-reference
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpcsStageConfig {
    
    /**
     * The stage name.
     */
    private String name;
    
    /**
     * Metadata cache time period.
     */
    private String metadataCache;
    
    /**
     * Resource requests and limits for the stage volume.
     */
    private SpcsStageResources resources;

    public SpcsStageConfig resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        SpcsStageResources resolvedResources = null;
        if (resources != null) {
            SpcsResourceRequests resolvedRequests = null;
            if (resources.getRequests() != null) {
                resolvedRequests = SpcsResourceRequests.builder()
                    .memory(resources.getRequests().getMemory() == null ? null : resolver.evaluateToString(resources.getRequests().getMemory(), context))
                    .cpu(resources.getRequests().getCpu() == null ? null : resolver.evaluateToString(resources.getRequests().getCpu(), context))
                    .build();
            }
            SpcsResourceLimits resolvedLimits = null;
            if (resources.getLimits() != null) {
                resolvedLimits = SpcsResourceLimits.builder()
                    .memory(resources.getLimits().getMemory() == null ? null : resolver.evaluateToString(resources.getLimits().getMemory(), context))
                    .cpu(resources.getLimits().getCpu() == null ? null : resolver.evaluateToString(resources.getLimits().getCpu(), context))
                    .build();
            }
            resolvedResources = SpcsStageResources.builder()
                .requests(resolvedRequests)
                .limits(resolvedLimits)
                .build();
        }
        return toBuilder()
            .name(name == null ? null : resolver.evaluateToString(name, context))
            .metadataCache(metadataCache == null ? null : resolver.evaluateToString(metadataCache, context))
            .resources(resolvedResources)
            .build();
    }
    
    /**
     * Resource configuration for stage volumes.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpcsStageResources {
        private SpcsResourceRequests requests;
        private SpcsResourceLimits limits;
    }
    
    /**
     * Resource requests.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpcsResourceRequests {
        private String memory;
        private String cpu;
    }
    
    /**
     * Resource limits.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpcsResourceLimits {
        private String memory;
        private String cpu;
    }
}
