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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for block storage volumes in SPCS.
 * 
 * Reference: https://docs.snowflake.com/en/developer-guide/snowpark-container-services/specification-reference
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpcsBlockConfig {
    
    /**
     * Initial contents configuration for block volumes.
     */
    private SpcsBlockInitialContents initialContents;
    
    /**
     * IOPS (Input/Output Operations Per Second) for the block volume.
     * Non-numeric or empty values are treated as optional (set to null).
     */
    @Setter(AccessLevel.NONE)
    @Getter
    private Integer iops;
    
    /**
     * Custom setter for iops that handles non-numeric values.
     * Since this field is optional, non-numeric or empty values are set to null.
     * This setter accepts String to handle cases where Spring Boot's binder passes the raw string value.
     */
    public void setIops(String value) {
        if (value == null) {
            this.iops = null;
            return;
        }
        
        String trimmed = value.trim();
        // Try to parse as integer
        try {
            this.iops = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            // If parsing fails, set to null (optional field)
            this.iops = null;
        }
    }
    
    /**
     * Setter that accepts Integer (for when Spring Boot successfully converts the value).
     * This is needed in addition to the String setter to handle both cases.
     */
    public void setIops(Integer value) {
        this.iops = value;
    }
    
    /**
     * Throughput in MiB per second for the block volume.
     */
    private String throughput;
    
    /**
     * Encryption type: SNOWFLAKE_SSE or SNOWFLAKE_FULL
     */
    private String encryption;
    
    /**
     * Whether to automatically snapshot the volume when the service is deleted.
     * When true, volumes can be safely deleted with the service.
     * Defaults to true for volumes managed by ShinyProxy.
     */
    private Boolean snapshotOnDelete;
    
    public SpcsBlockConfig resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        SpcsBlockInitialContents resolvedInitialContents = null;
        if (initialContents != null) {
            String resolvedFromSnapshot = initialContents.getFromSnapshot() == null
                ? null
                : resolver.evaluateToString(initialContents.getFromSnapshot(), context);
            resolvedInitialContents = SpcsBlockInitialContents.builder()
                .fromSnapshot(resolvedFromSnapshot)
                .build();
        }
        return toBuilder()
            .throughput(throughput == null ? null : resolver.evaluateToString(throughput, context))
            .encryption(encryption == null ? null : resolver.evaluateToString(encryption, context))
            .initialContents(resolvedInitialContents)
            .build();
    }

    /**
     * Initial contents configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpcsBlockInitialContents {
        /**
         * Snapshot name to use as initial contents.
         */
        private String fromSnapshot;
    }
}
