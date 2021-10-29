/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
import java.util.stream.Collectors;

public class ProxySpec {

	private String id;
	private String displayName;
	private String description;
	private String logoURL;

	private ProxyAccessControl accessControl;
	private List<ContainerSpec> containerSpecs;
	private List<RuntimeSettingSpec> runtimeSettingSpecs;

	private Map<String, String> settings = new HashMap<>();
	
	private String kubernetesPodPatches;
	private List<String> kubernetesAdditionalManifests = new ArrayList<>();
	private List<String> kubernetesAdditionalPersistentManifests = new ArrayList<>();

	private Long maxLifeTime;
	private Boolean stopOnLogout;
	private Long heartbeatTimeout;

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
	
	public ContainerSpec getContainerSpec(String image) {
		if (image == null || image.isEmpty()) return null;
		return containerSpecs.stream().filter(s -> {
			if (image.endsWith(":latest") && !s.getImage().contains(":")) {
				// if we query for the latest image and the spec does not contain a tag -> then add :latest to the
                // image name of the spec.
				// e.g. querying for "debian:latest" while "debian" is specified in the spec
				return image.equals(s.getImage() + ":latest");
			} else {
				return image.equals(s.getImage());
			}
		}).findAny().orElse(null);
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
	public String getKubernetesPodPatch() {
		return kubernetesPodPatches;
	}

	public void setKubernetesPodPatches(String kubernetesPodPatches) {
		this.kubernetesPodPatches = kubernetesPodPatches;
	}

	public void setKubernetesAdditionalManifests(List<String> manifests) {
		this.kubernetesAdditionalManifests = manifests;
	}

	public List<String> getKubernetesAdditionalManifests() {
		return kubernetesAdditionalManifests;
	}

	public void setKubernetesAdditionalPersistentManifests(List<String> manifests) {
		this.kubernetesAdditionalPersistentManifests = manifests;
	}

	public List<String> getKubernetesAdditionalPersistentManifests() {
		return kubernetesAdditionalPersistentManifests;
	}

	public Long getMaxLifeTime() {
		return maxLifeTime;
	}

	public void setMaxLifeTime(Long maxLifeTime) {
		this.maxLifeTime = maxLifeTime;
	}

	public Boolean stopOnLogout() {
		return stopOnLogout;
	}

	public void setStopOnLogout(Boolean stopOnLogout) {
		this.stopOnLogout = stopOnLogout;
	}

	public Long getHeartbeatTimeout() {
		return heartbeatTimeout;
	}

	public void setHeartbeatTimeout(Long heartbeatTimeout) {
		this.heartbeatTimeout = heartbeatTimeout;
	}

	public void copy(ProxySpec target) {
		target.setId(id);
		target.setDisplayName(displayName);
		target.setDescription(description);
		target.setLogoURL(logoURL);
		target.setMaxLifeTime(maxLifeTime);
		target.setStopOnLogout(stopOnLogout);
		target.setHeartbeatTimeout(heartbeatTimeout);

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
			target.setKubernetesPodPatches(kubernetesPodPatches);
		}
		
		if (kubernetesAdditionalManifests != null) {
			target.setKubernetesAdditionalManifests(new ArrayList<>(kubernetesAdditionalManifests));
		}

		if (kubernetesAdditionalPersistentManifests != null) {
			target.setKubernetesAdditionalPersistentManifests(new ArrayList<>(kubernetesAdditionalPersistentManifests));
		}

	}

}
