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
package eu.openanalytics.containerproxy.backend.strategy.impl;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.backend.strategy.IProxyTargetMappingStrategy;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;

@Component
public class DefaultTargetMappingStrategy implements IProxyTargetMappingStrategy {

	public static final String DEFAULT_MAPPING_KEY = "default";
	
	public String createMapping(String mappingKey, Container container, Proxy proxy) {
		String mapping = proxy.getId();
		if (!mappingKey.equalsIgnoreCase(DEFAULT_MAPPING_KEY)) {
			// For non-default mappings, also append the mapping key
			mapping += "/" + mappingKey;
		}
		return mapping;
	}

}
