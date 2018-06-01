package eu.openanalytics.containerproxy.spec;

import java.util.Set;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;

/**
 * A provider of base (predefined) ProxySpecs, e.g. from the application's configuration file.
 */
public interface IProxySpecProvider {

	public Set<ProxySpec> getSpecs();
	
	public ProxySpec getSpec(String id);

}
