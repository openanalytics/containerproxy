package eu.openanalytics.containerproxy.model.runtime;

import java.util.HashMap;
import java.util.Map;

import eu.openanalytics.containerproxy.model.spec.ContainerSpec;

public class Container {

	private String id;
	private ContainerSpec spec;

	private Map<String, Object> parameters = new HashMap<>();
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public ContainerSpec getSpec() {
		return spec;
	}
	public void setSpec(ContainerSpec spec) {
		this.spec = spec;
	}
	public Map<String, Object> getParameters() {
		return parameters;
	}
	public void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}

}
