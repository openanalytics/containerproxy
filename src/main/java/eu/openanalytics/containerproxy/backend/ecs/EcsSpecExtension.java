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
package eu.openanalytics.containerproxy.backend.ecs;

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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // force Spring to not use constructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class EcsSpecExtension extends AbstractSpecExtension {

    @Builder.Default
    SpelField.String ecsTaskRole = new SpelField.String();

    @Builder.Default
    SpelField.String ecsExecutionRole = new SpelField.String();

    @Builder.Default
    SpelField.String ecsCpuArchitecture = new SpelField.String();

    @Builder.Default
    SpelField.String ecsOperationSystemFamily = new SpelField.String();

    @Builder.Default
    SpelField.Integer ecsEphemeralStorageSize = new SpelField.Integer();

    @Builder.Default
    List<EcsEfsVolume> ecsEfsVolumes = new ArrayList<>();

    @Builder.Default
    List<EcsManagedSecret> ecsManagedSecrets = new ArrayList<>();

    @Builder.Default
    SpelField.Boolean ecsEnableExecuteCommand = new SpelField.Boolean();

    @Builder.Default
    SpelField.String ecsRepositoryCredentialsParameter = new SpelField.String();

    @Override
    public ISpecExtension firstResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
            .ecsTaskRole(ecsTaskRole.resolve(resolver, context))
            .ecsExecutionRole(ecsExecutionRole.resolve(resolver, context))
            .ecsCpuArchitecture(ecsCpuArchitecture.resolve(resolver, context))
            .ecsOperationSystemFamily(ecsOperationSystemFamily.resolve(resolver, context))
            .ecsEphemeralStorageSize(ecsEphemeralStorageSize.resolve(resolver, context))
            .ecsEfsVolumes(ecsEfsVolumes.stream().map(p -> p.resolve(resolver, context)).collect(Collectors.toList()))
            .ecsManagedSecrets(ecsManagedSecrets.stream().map(p -> p.resolve(resolver, context)).collect(Collectors.toList()))
            .ecsEnableExecuteCommand(ecsEnableExecuteCommand.resolve(resolver, context))
            .ecsRepositoryCredentialsParameter(ecsRepositoryCredentialsParameter.resolve(resolver, context))
            .build();
    }

    @Override
    public ISpecExtension finalResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return this;
    }

}
