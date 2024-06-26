/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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

import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CacheHeadersMode;
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
@AllArgsConstructor(access = AccessLevel.PRIVATE) // force Spring to not use constructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ProxySpec {

    @JsonView(Views.UserApi.class)
    String id;

    @JsonView(Views.UserApi.class)
    String displayName;

    @JsonView(Views.UserApi.class)
    String description;

    String logoURL;

    @JsonView(Views.UserApi.class)
    String logoWidth;

    @JsonView(Views.UserApi.class)
    String logoHeight;

    @JsonView(Views.UserApi.class)
    String logoStyle;

    String faviconPath;

    @JsonView(Views.UserApi.class)
    String logoClasses;

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
    Map<String, ISpecExtension> specExtensions = new HashMap<>();

    Boolean addDefaultHttpHeaders;

    @Builder.Default
    SpelField.StringMap httpHeaders = new SpelField.StringMap();

    CacheHeadersMode cacheHeadersMode;

    @Builder.Default
    Integer maxTotalInstances = -1;

    public void setContainerIndex() {
        if (this.containerSpecs != null) {
            for (int i = 0; i < this.containerSpecs.size(); i++) {
                this.containerSpecs
                    .get(i)
                    .setIndex(i);
            }
        }
    }

    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = new SpelField.StringMap(httpHeaders);
    }

    public void addSpecExtension(ISpecExtension specExtension) {
        specExtensions.put(specExtension.getClass().getName(), specExtension);
    }

    public <T> T getSpecExtension(Class<T> extensionClass) {
        return extensionClass.cast(specExtensions.get(extensionClass.getName()));
    }

    public ProxySpec firstResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
            .heartbeatTimeout(heartbeatTimeout.resolve(resolver, context))
            .maxLifeTime(maxLifeTime.resolve(resolver, context))
            .specExtensions(
                specExtensions.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().firstResolve(resolver, context))))
            .containerSpecs(
                containerSpecs
                    .stream()
                    .map(c -> c.firstResolve(resolver, context.copy(c)))
                    .toList()
            )
            .build();
    }

    public ProxySpec finalResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return toBuilder()
            .httpHeaders(httpHeaders.resolve(resolver, context))
            .specExtensions(
                specExtensions.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().finalResolve(resolver, context))))
            .containerSpecs(
                containerSpecs
                    .stream()
                    .map(c -> c.finalResolve(resolver, context.copy(c)))
                    .toList()
            )
            .build();
    }
}
