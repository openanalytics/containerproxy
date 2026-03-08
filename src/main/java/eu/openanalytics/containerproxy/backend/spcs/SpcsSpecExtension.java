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

import eu.openanalytics.containerproxy.model.spec.AbstractSpecExtension;
import eu.openanalytics.containerproxy.model.spec.ISpecExtension;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.spec.expression.SpelField;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EqualsAndHashCode(callSuper = true)
@Data
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // force Spring to not use constructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class SpcsSpecExtension extends AbstractSpecExtension {

    @Builder.Default
    SpelField.String spcsComputePool = new SpelField.String();

    @Builder.Default
    SpelField.String spcsDatabase = new SpelField.String();

    @Builder.Default
    SpelField.String spcsSchema = new SpelField.String();

    /**
     * Volumes for the SPCS service (defined at spec level; one service per proxy).
     */
    @Builder.Default
    java.util.List<SpcsVolume> spcsVolumes = new java.util.ArrayList<>();
    
    /**
     * External access integrations for the SPCS service.
     */
    @Builder.Default
    java.util.List<String> spcsExternalAccessIntegrations = new java.util.ArrayList<>();
    
    @Builder.Default
    java.util.List<SpcsSecret> spcsSecrets = new java.util.ArrayList<>();
    
    SpcsReadinessProbe spcsReadinessProbe;

    @Override
    public ISpecExtension firstResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
            .spcsComputePool(spcsComputePool.resolve(resolver, context))
            .spcsDatabase(spcsDatabase.resolve(resolver, context))
            .spcsSchema(spcsSchema.resolve(resolver, context))
            .spcsExternalAccessIntegrations(
                spcsExternalAccessIntegrations != null
                    ? spcsExternalAccessIntegrations.stream()
                        .map(s -> resolver.evaluateToString(s, context))
                        .toList()
                    : null)
            .spcsVolumes(spcsVolumes != null
                ? spcsVolumes.stream().map(v -> v.resolve(resolver, context)).toList()
                : null)
            .spcsSecrets(spcsSecrets != null
                ? spcsSecrets.stream().map(s -> s.resolve(resolver, context)).toList()
                : null)
            .spcsReadinessProbe(spcsReadinessProbe != null
                ? spcsReadinessProbe.resolve(resolver, context)
                : null)
            .build();
    }

    @Override
    public ISpecExtension finalResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return this;
    }

}

