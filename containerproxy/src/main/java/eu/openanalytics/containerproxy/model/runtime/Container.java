package eu.openanalytics.containerproxy.model.runtime;

import java.util.Map;

import eu.openanalytics.containerproxy.model.spec.ContainerSpec;

public class Container {

	private String id;
	private String name;
	private ContainerSpec spec;

	private Map<String, Object> parameters;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
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
