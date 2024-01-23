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
package eu.openanalytics.containerproxy.spec.impl;

import eu.openanalytics.containerproxy.model.spec.ISpecExtension;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.ISpecExtensionProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "proxy")
public class DefaultSpecProvider implements IProxySpecProvider {

    @Inject
    private List<ISpecExtensionProvider<?>> specExtensionProviders;

    private List<ProxySpec> specs = new ArrayList<>();

    public List<ProxySpec> getSpecs() {
        return new ArrayList<>(specs);
    }

    public void setSpecs(List<ProxySpec> specs) {
        this.specs = specs;
    }

    public ProxySpec getSpec(String id) {
        if (id == null || id.isEmpty()) return null;
        return specs.stream().filter(s -> id.equals(s.getId())).findAny().orElse(null);
    }

    @PostConstruct
    public void init() {
        specs.forEach(ProxySpec::setContainerIndex);
        for (ISpecExtensionProvider<?> specExtensionProvider : specExtensionProviders) {
            if (specExtensionProvider.getSpecs() != null) {
                for (ISpecExtension specExtension : specExtensionProvider.getSpecs()) {
                    getSpec(specExtension.getId()).addSpecExtension(specExtension);
                }
            }
        }
    }

}
