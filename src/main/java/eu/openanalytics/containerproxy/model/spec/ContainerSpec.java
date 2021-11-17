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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerSpec {

	private String image;
	private String[] cmd;
	private Map<String, String> env;
	private String envFile;
	private String network;
	private String[] networkConnections;
	private String[] dns;
	private String[] volumes;
	private Map<String, Integer> portMapping = new HashMap<>();
	private boolean privileged;
	private String memoryRequest;
	private String memoryLimit;
	private String cpuRequest;
	private String cpuLimit;
	private String targetPath;
	private Map<String, String> labels = new HashMap<>();
	private Map<String, String> settings = new HashMap<>();
	private List<DockerSwarmSecret> dockerSwarmSecrets = new ArrayList();
	private String dockerRegistryDomain;
	private String dockerRegistryUsername;
	private String dockerRegistryPassword;

	public String getImage() {
		return image;
	}
	public void setImage(String image) {
		this.image = image;
	}
	public String[] getCmd() {
		return cmd;
	}
	public void setCmd(String[] cmd) {
		this.cmd = cmd;
	}
	public Map<String, String> getEnv() {
		return env;
	}
	public void setEnv(Map<String, String> env) {
		this.env = env;
	}
	public String getEnvFile() {
		return envFile;
	}
	public void setEnvFile(String envFile) {
		this.envFile = envFile;
	}
	public String getNetwork() {
		return network;
	}
	public void setNetwork(String network) {
		this.network = network;
	}
	public String[] getNetworkConnections() {
		return networkConnections;
	}
	public void setNetworkConnections(String[] networkConnections) {
		this.networkConnections = networkConnections;
	}
	public String[] getDns() {
		return dns;
	}
	public void setDns(String[] dns) {
		this.dns = dns;
	}
	public String[] getVolumes() {
		return volumes;
	}
	public void setVolumes(String[] volumes) {
		this.volumes = volumes;
	}
	public Map<String, Integer> getPortMapping() {
		return portMapping;
	}
	public void setPortMapping(Map<String, Integer> portMapping) {
		this.portMapping = portMapping;
	}
	public boolean isPrivileged() {
		return privileged;
	}
	public void setPrivileged(boolean privileged) {
		this.privileged = privileged;
	}
	public String getMemoryRequest() {
		return memoryRequest;
	}
	public void setMemoryRequest(String memoryRequest) {
		this.memoryRequest = memoryRequest;
	}
	public String getMemoryLimit() {
		return memoryLimit;
	}
	public void setMemoryLimit(String memoryLimit) {
		this.memoryLimit = memoryLimit;
	}
	public String getCpuRequest() {
		return cpuRequest;
	}
	public void setCpuRequest(String cpuRequest) {
		this.cpuRequest = cpuRequest;
	}
	public String getCpuLimit() {
		return cpuLimit;
	}
	public void setCpuLimit(String cpuLimit) {
		this.cpuLimit = cpuLimit;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = labels;
	}

	public Map<String, String> getSettings() {
		return settings;
	}
	
	public void setSettings(Map<String, String> settings) {
		this.settings = settings;
	}

	public String getTargetPath() {
		return targetPath;
	}

	public void setTargetPath(String targetPath) {
		this.targetPath = targetPath;
	}

	public List<DockerSwarmSecret> getDockerSwarmSecrets() {
		return dockerSwarmSecrets;
	}

	public void setDockerSwarmSecrets(List<DockerSwarmSecret> dockerSwarmSecrets) {
		this.dockerSwarmSecrets = dockerSwarmSecrets;
	}

	public String getDockerRegistryDomain() {
		return dockerRegistryDomain;
	}

	public void setDockerRegistryDomain(String dockerRegistryDomain) {
		this.dockerRegistryDomain = dockerRegistryDomain;
	}

	public String getDockerRegistryUsername() {
		return dockerRegistryUsername;
	}

	public void setDockerRegistryUsername(String dockerRegistryUsername) {
		this.dockerRegistryUsername = dockerRegistryUsername;
	}

	public String getDockerRegistryPassword() {
		return dockerRegistryPassword;
	}

	public void setDockerRegistryPassword(String dockerRegistryPassword) {
		this.dockerRegistryPassword = dockerRegistryPassword;
	}

	public void copy(ContainerSpec target) {
		target.setImage(image);
		if (cmd != null) target.setCmd(Arrays.copyOf(cmd, cmd.length));
		if (env != null) {
			if (target.getEnv() == null) target.setEnv(new HashMap<>());
			target.getEnv().putAll(env);
		}
		target.setEnvFile(envFile);
		target.setNetwork(network);
		if (networkConnections != null) target.setNetworkConnections(Arrays.copyOf(networkConnections, networkConnections.length));
		if (dns != null) target.setDns(Arrays.copyOf(dns, dns.length));
		if (volumes != null) target.setVolumes(Arrays.copyOf(volumes, volumes.length));
		if (portMapping != null) {
			if (target.getPortMapping() == null) target.setPortMapping(new HashMap<>());
			target.getPortMapping().putAll(portMapping);
		}
		target.setMemoryRequest(memoryRequest);
		target.setMemoryLimit(memoryLimit);
		target.setCpuRequest(cpuRequest);
		target.setCpuLimit(cpuLimit);
		target.setPrivileged(privileged);
		if (labels != null) {
			if (target.getLabels() == null) target.setLabels(new HashMap<>());
			target.getLabels().putAll(labels);
		}
		if (settings != null) {
			if (target.getSettings() == null) target.setSettings(new HashMap<>());
			target.getSettings().putAll(settings);
		}
		if (dockerSwarmSecrets != null) {
			if (target.getDockerSwarmSecrets() == null) target.setDockerSwarmSecrets(new ArrayList<>());
			target.getDockerSwarmSecrets().addAll(dockerSwarmSecrets);
		}
		target.setDockerRegistryDomain(dockerRegistryDomain);
		target.setDockerRegistryUsername(dockerRegistryUsername);
		target.setDockerRegistryPassword(dockerRegistryPassword);
		target.setTargetPath(targetPath);
	}
}
