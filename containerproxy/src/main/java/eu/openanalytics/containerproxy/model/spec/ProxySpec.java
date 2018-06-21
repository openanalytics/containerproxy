/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.model.spec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProxySpec {

	private String id;
	private String displayName;
	private String description;
	private String logoURL;
	
	private ProxyAccessControl accessControl;
	private List<ContainerSpec> containerSpecs;
	private List<RuntimeSettingSpec> runtimeSettingSpecs;

	private Map<String, String> settings = new HashMap<>();

	public ProxySpec() {
		settings = new HashMap<>();
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	public ProxyAccessControl getAccessControl() {
		return accessControl;
	}

	public void setAccessControl(ProxyAccessControl accessControl) {
		this.accessControl = accessControl;
	}

	public List<ContainerSpec> getContainerSpecs() {
		return containerSpecs;
	}
	
	public void setContainerSpecs(List<ContainerSpec> containerSpecs) {
		this.containerSpecs = containerSpecs;
	}
	
	public List<RuntimeSettingSpec> getRuntimeSettingSpecs() {
		return runtimeSettingSpecs;
	}
	
	public void setRuntimeSettingSpecs(List<RuntimeSettingSpec> runtimeSettingSpecs) {
		this.runtimeSettingSpecs = runtimeSettingSpecs;
	}
	
	public Map<String, String> getSettings() {
		return settings;
	}

	public void setSettings(Map<String, String> settings) {
		this.settings = settings;
	}
	
	public void copy(ProxySpec target) {
		target.setId(id);
		target.setDisplayName(displayName);
		target.setDescription(description);
		target.setLogoURL(logoURL);
		
		if (accessControl != null) {
			if (target.getAccessControl() == null) target.setAccessControl(new ProxyAccessControl());
			accessControl.copy(target.getAccessControl());
		}
		
		if (containerSpecs != null) {
			if (target.getContainerSpecs() == null) target.setContainerSpecs(new ArrayList<>());
			for (ContainerSpec spec: containerSpecs) {
				ContainerSpec copy = new ContainerSpec();
				spec.copy(copy);
				target.getContainerSpecs().add(copy);
			}
		}
		
		if (runtimeSettingSpecs != null) {
			if (target.getRuntimeSettingSpecs() == null) target.setRuntimeSettingSpecs(new ArrayList<>());
			for (RuntimeSettingSpec spec: runtimeSettingSpecs) {
				RuntimeSettingSpec copy = new RuntimeSettingSpec();
				spec.copy(copy);
				target.getRuntimeSettingSpecs().add(copy);
			}
		}
		
		if (settings != null) {
			if (target.getSettings() == null) target.setSettings(new HashMap<>());
			target.getSettings().putAll(settings);
		}
	}
}
