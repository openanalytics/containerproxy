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

import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.spec.expression.SpelField;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Data
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // force Spring to not use constructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class EcsEfsVolume {

    @Builder.Default
    SpelField.String name = new SpelField.String();

    @Builder.Default
    SpelField.String fileSystemId = new SpelField.String();

    @Builder.Default
    SpelField.String rootDirectory = new SpelField.String();

    @Builder.Default
    SpelField.Boolean transitEncryption = new SpelField.Boolean();

    @Builder.Default
    SpelField.Integer transitEncryptionPort = new SpelField.Integer();

    @Builder.Default
    SpelField.String accessPointId = new SpelField.String();

    @Builder.Default
    SpelField.Boolean enableIam = new SpelField.Boolean();

    public EcsEfsVolume resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
            .name(name.resolve(resolver, context))
            .fileSystemId(fileSystemId.resolve(resolver, context))
            .rootDirectory(rootDirectory.resolve(resolver, context))
            .transitEncryption(transitEncryption.resolve(resolver, context))
            .transitEncryptionPort(transitEncryptionPort.resolve(resolver, context))
            .accessPointId(accessPointId.resolve(resolver, context))
            .enableIam(enableIam.resolve(resolver, context))
            .build();
    }

}
