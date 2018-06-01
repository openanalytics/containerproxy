package eu.openanalytics.containerproxy.spec.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

@Service
@ConfigurationProperties(prefix = "proxy")
public class DefaultSpecProvider implements IProxySpecProvider {
	
	private List<ProxySpec> specs = new ArrayList<>();
	
	public Set<ProxySpec> getSpecs() {
		return new HashSet<>(specs);
	}
	
	public ProxySpec getSpec(String id) {
		if (id == null || id.isEmpty()) return null;
		return specs.stream().filter(s -> id.equals(s.getId())).findAny().orElse(null);
	}
	
	public void setSpecs(List<ProxySpec> specs) {
		this.specs = specs;
	}
	
}
