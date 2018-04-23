package eu.openanalytics.containerproxy.backend.ext;

import eu.openanalytics.containerproxy.model.Container;
import eu.openanalytics.containerproxy.model.Proxy;

public class DefaultTargetMappingStrategy implements ITargetMappingStrategy {

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
