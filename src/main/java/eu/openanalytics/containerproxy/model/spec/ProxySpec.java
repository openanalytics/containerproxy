/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.containerproxy.model.spec;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ProxySpec {

    String id;
    String displayName;
    String description;
    String logoURL;

    AccessControl accessControl;

    @Builder.Default
    List<ContainerSpec> containerSpecs = new ArrayList<>();

    Parameters parameters;

    @Builder.Default
    SpelField.Long maxLifeTime = new SpelField.Long();

    Boolean stopOnLogout;
    @Builder.Default
    SpelField.Long heartbeatTimeout = new SpelField.Long();

    @Builder.Default
    Map<Class<? extends ISpecExtension>, ISpecExtension> specExtensions = new HashMap<>();

    public ContainerSpec getContainerSpec(String image) {
        if (image == null || image.isEmpty()) return null;
        // we compare with the original value here, when AppRecovery tries to recover the spec, we don't interpret spel
        return containerSpecs.stream().filter(s -> {
            if (image.endsWith(":latest") && !s.getImage().getOriginalValue().contains(":")) {
                // if we query for the latest image and the spec does not contain a tag -> then add :latest to the
                // image name of the spec.
                // e.g. querying for "debian:latest" while "debian" is specified in the spec
                return image.equals(s.getImage() + ":latest");
            } else {
                return image.equals(s.getImage().getOriginalValue());
            }
        }).findAny().orElse(null);
    }

    public void setContainerIndex() {
        for (int i = 0; i < this.containerSpecs.size(); i++) {
            this.containerSpecs.get(i).setIndex(i);
        }
    }

    public void addSpecExtension(ISpecExtension specExtension) {
        specExtensions.put(specExtension.getClass(), specExtension);
    }

    public <T> T getSpecExtension(Class<T> extensionClass) {
        return extensionClass.cast(specExtensions.get(extensionClass));
    }

    public ProxySpec resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
                .heartbeatTimeout(heartbeatTimeout.resolve(resolver, context))
                .maxLifeTime(maxLifeTime.resolve(resolver, context))
                .specExtensions(
                        specExtensions.entrySet()
                                .stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> e.getValue().resolve(resolver, context))))
                .containerSpecs(
                        containerSpecs
                                .stream()
                                .map(c -> c.resolve(resolver, context.copy(c)))
                                .collect(Collectors.toList())
                )
                .build();
    }

}
