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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

import javax.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "proxy")
public class DefaultSpecProvider implements IProxySpecProvider {
	
	private List<ProxySpec> specs = new ArrayList<>();
	
	public List<ProxySpec> getSpecs() {
		return new ArrayList<>(specs);
	}
	
	public ProxySpec getSpec(String id) {
		if (id == null || id.isEmpty()) return null;
		return specs.stream().filter(s -> id.equals(s.getId())).findAny().orElse(null);
	}
	
	public void setSpecs(List<ProxySpec> specs) {
		this.specs = specs;
	}

	@PostConstruct
	public void init() {
		specs.forEach(ProxySpec::setContainerIndex);
	}
	
}
