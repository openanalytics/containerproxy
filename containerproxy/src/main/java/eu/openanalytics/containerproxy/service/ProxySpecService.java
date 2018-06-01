package eu.openanalytics.containerproxy.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecMergeStrategy;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.ProxySpecException;
import eu.openanalytics.containerproxy.spec.impl.DefaultSpecMergeStrategy;

@Service
public class ProxySpecService {

	@Autowired
	private IProxySpecProvider baseSpecProvider;
	
	@Autowired(required=false)
	private IProxySpecMergeStrategy mergeStrategy = new DefaultSpecMergeStrategy();
	
	public Set<ProxySpec> getSpecs() {
		return baseSpecProvider.getSpecs();
	}
	
	public ProxySpec getSpec(String id) {
		return baseSpecProvider.getSpec(id);
	}
	
	public ProxySpec resolveSpec(String baseSpecId, ProxySpec runtimeSpec, Set<RuntimeSetting> runtimeSettings) throws ProxySpecException {
		ProxySpec baseSpec = baseSpecProvider.getSpec(baseSpecId);
		ProxySpec finalSpec = mergeStrategy.merge(baseSpec, runtimeSpec, runtimeSettings);
		return finalSpec;
	}
}
