/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.JsonPatch;
import javax.json.JsonValue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;

public class ProxySpec {

	private String id;
	private String displayName;
	private String description;
	private String logoURL;
	
	private ProxyAccessControl accessControl;
	private List<ContainerSpec> containerSpecs;
	private List<RuntimeSettingSpec> runtimeSettingSpecs;

	private Map<String, String> settings = new HashMap<>();
	
	private JsonPatch kubernetesPodPatches;
	private List<String> kubernetesAdditionalManifests = new ArrayList<>();

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

	/**
	 * Returns the Kubernetes Pod Patch as JsonValue (i.e. array) for nice representation in API requests.
	 */
	public JsonValue getKubernetesPodPatch() {
		if (this.kubernetesPodPatches == null) {
			return null;
		} else {
			return kubernetesPodPatches.toJsonArray();
		}
	}

	/**
	 * Returns the Kubernetes Pod Patch as a JsonPatch, so it can be directly be used to patch the spec.
	 * Should not be returned by API responses.
	 */
	@JsonIgnore
	public JsonPatch getKubernetesPodPatchAsJsonpatch() {
		return kubernetesPodPatches;
	}

	public void setKubernetesPodPatches(String kubernetesPodPatches) throws JsonParseException, JsonMappingException, IOException {
		try {
			// convert the raw YAML string into a JsonPatch
			ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
			yamlReader.registerModule(new JSR353Module());
			this.kubernetesPodPatches = yamlReader.readValue(kubernetesPodPatches, JsonPatch.class);
		} catch (Exception exception) {
			exception.printStackTrace(); // log the exception for easier debugging
			throw exception;
		}
	}

	private void setKubernetesPodPatches(JsonPatch kubernetesPodPatches) {
		this.kubernetesPodPatches = kubernetesPodPatches;
	}

	public void setKubernetesAdditionalManifests(List<String> manifests) {
		this.kubernetesAdditionalManifests = manifests;
	}

	public List<String> getKubernetesAdditionalManifests() {
		return kubernetesAdditionalManifests;
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
		
		
		if (kubernetesPodPatches != null) {
			// JsonPatch is an immutable object
			target.setKubernetesPodPatches(kubernetesPodPatches);
		}
		
		if (kubernetesAdditionalManifests != null) {
			target.setKubernetesAdditionalManifests(kubernetesAdditionalManifests.stream().collect(Collectors.toList()));
		}
		
	}

}
