package eu.openanalytics.containerproxy.backend.strategy.impl;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.backend.strategy.IProxyTargetMappingStrategy;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;

@Component
public class DefaultTargetMappingStrategy implements IProxyTargetMappingStrategy {

	public static final String DEFAULT_MAPPING_KEY = "default";
	
	public String createMapping(String mappingKey, Container container, Proxy proxy) {
		String mapping = "endpoint/" + proxy.getId();
		if (!mappingKey.equalsIgnoreCase(DEFAULT_MAPPING_KEY)) {
			// For non-default mappings, also append the mapping key
			mapping += "/" + mappingKey;
		}
		return mapping;
	}

}
