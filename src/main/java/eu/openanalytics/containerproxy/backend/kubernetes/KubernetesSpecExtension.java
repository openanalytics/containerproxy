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
package eu.openanalytics.containerproxy.backend.kubernetes;

import eu.openanalytics.containerproxy.model.spec.AbstractSpecExtension;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
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
@AllArgsConstructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class KubernetesSpecExtension extends AbstractSpecExtension {

    String kubernetesPodPatches;

    @Builder.Default
    List<String> kubernetesAdditionalManifests = new ArrayList<>();

    @Builder.Default
    List<String> kubernetesAdditionalPersistentManifests = new ArrayList<>();

    @Override
    public KubernetesSpecExtension firstResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return this;
    }

    @Override
    public KubernetesSpecExtension finalResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
                .kubernetesAdditionalManifests(kubernetesAdditionalManifests.stream().map(m -> resolver.evaluateToString(m, context)).collect(Collectors.toList()))
                .kubernetesAdditionalPersistentManifests(kubernetesAdditionalPersistentManifests.stream().map(m -> resolver.evaluateToString(m, context)).collect(Collectors.toList()))
                .kubernetesPodPatches(resolver.evaluateToString(kubernetesPodPatches, context))
                .build();
    }

}
