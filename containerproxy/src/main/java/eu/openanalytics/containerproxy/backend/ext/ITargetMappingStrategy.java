package eu.openanalytics.containerproxy.backend.ext;

import eu.openanalytics.containerproxy.model.Container;
import eu.openanalytics.containerproxy.model.Proxy;

public interface ITargetMappingStrategy {

	public String createMapping(String mappingKey, Container container, Proxy proxy);

}
