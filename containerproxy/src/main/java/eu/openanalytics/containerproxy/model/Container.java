package eu.openanalytics.containerproxy.model;

public class Container {

	private String id;
	private String name;
	private ContainerSpec spec;

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
	
}
