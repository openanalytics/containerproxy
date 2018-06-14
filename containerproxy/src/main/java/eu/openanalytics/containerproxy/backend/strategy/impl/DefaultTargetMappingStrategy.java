package eu.openanalytics.containerproxy.backend.strategy.impl;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.backend.strategy.IProxyTargetMappingStrategy;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;

@Component
public class DefaultTargetMappingStrategy implements IProxyTargetMappingStrategy {

	public static final String DEFAULT_MAPPING_KEY = "default";
	
	public String createMapping(String mappingKey, Container container, Proxy proxy) {
		String mapping = container.getId();
		if (mappingKey.equalsIgnoreCase(DEFAULT_MAPPING_KEY)) {
			// Just use the container id.
		} else {
			// Also append the key to the container id.
			mapping += "/" + mappingKey;
		}
		return mapping;
	}

}
