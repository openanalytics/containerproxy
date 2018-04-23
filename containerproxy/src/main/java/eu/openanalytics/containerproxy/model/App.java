package eu.openanalytics.containerproxy.model;

import java.util.List;
import java.util.Map;

public class App {

	private String name;
	private String displayName;
	private String description;
	private String logoURL;
	
	private AppAccessControl accessControl;
	private List<ContainerSpec> containerSpecs;
	
	private Map<String, String> settings;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLogoURL() {
		return logoURL;
	}

	public void setLogoURL(String logoURL) {
		this.logoURL = logoURL;
	}

	public AppAccessControl getAccessControl() {
		return accessControl;
	}

	public void setAccessControl(AppAccessControl accessControl) {
		this.accessControl = accessControl;
	}

	public List<ContainerSpec> getContainerSpecs() {
		return containerSpecs;
	}
	
	public void setContainerSpecs(List<ContainerSpec> containerSpecs) {
		this.containerSpecs = containerSpecs;
	}
	
	public Map<String, String> getSettings() {
		return settings;
	}

	public void setSettings(Map<String, String> settings) {
		this.settings = settings;
	}
	
}
