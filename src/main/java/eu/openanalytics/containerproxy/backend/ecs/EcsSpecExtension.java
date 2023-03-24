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
package eu.openanalytics.containerproxy.backend.ecs;

import eu.openanalytics.containerproxy.model.spec.AbstractSpecExtension;
import eu.openanalytics.containerproxy.model.spec.ISpecExtension;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import lombok.*;

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
public class EcsSpecExtension extends AbstractSpecExtension {
    @Builder.Default
    List<String> securityGroups = new ArrayList<>();

    @Builder.Default
    List<String> subnets = new ArrayList<>();

    @Override
    public ISpecExtension firstResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return this;
    }

    @Override
    public ISpecExtension finalResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
                .securityGroups(securityGroups.stream().map(m -> resolver.evaluateToString(m, context)).collect(Collectors.toList()))
                .subnets(subnets.stream().map(m -> resolver.evaluateToString(m, context)).collect(Collectors.toList()))
                .build();
    }
}
