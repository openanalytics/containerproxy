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
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

public class AbstractSpecExtensionProvider<T extends ISpecExtension> {

    private List<T> specs;

    @Inject
    private IProxySpecProvider proxySpecProvider;

    @PostConstruct
    public void postInit() {
        if (specs != null) {
            specs.forEach(specExtension -> {
                proxySpecProvider.getSpec(specExtension.getId()).addSpecExtension(specExtension);
            });
        }
    }

    public void setSpecs(List<T> specs) {
        this.specs = specs;
    }

    public List<T> getSpecs() {
        return specs;
    }

}
