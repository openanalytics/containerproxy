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
 * Represents a readiness probe configuration for SPCS containers.
 * 
 * The readiness probe is used to determine when a container is ready to accept traffic.
 * It checks an HTTP endpoint on the specified port and path.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpcsReadinessProbe {
    
    /**
     * The TCP port number to probe.
     */
    private Integer port;
    
    /**
     * The HTTP path to probe.
     */
    private String path;

    public SpcsReadinessProbe resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
            .port(port == null ? null : resolver.evaluateToInteger(String.valueOf(port), context))
            .path(path == null ? null : resolver.evaluateToString(path, context))
            .build();
    }
}
