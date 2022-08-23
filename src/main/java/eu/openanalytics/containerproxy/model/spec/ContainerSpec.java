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

import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.spec.expression.SpelField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerSpec {

	/**
	 * Index in the array of ContainerSpecs of the ProxySpec.
	 */
	private Integer index;
	private SpelField.String image; // r
	private SpelField.StringList cmd = new SpelField.StringList();
	private Map<String, String> env; // TODO
	private SpelField.String envFile = new SpelField.String();
	private SpelField.String network = new SpelField.String();
	private SpelField.StringList networkConnections = new SpelField.StringList();
	private SpelField.StringList dns = new SpelField.StringList();
	private SpelField.StringList volumes = new SpelField.StringList();
	private Map<String, Integer> portMapping = new HashMap<>(); // TODO
	private boolean privileged;
	private SpelField.String memoryRequest = new SpelField.String();
	private SpelField.String memoryLimit = new SpelField.String();
	private SpelField.String cpuRequest = new SpelField.String();
	private SpelField.String cpuLimit = new SpelField.String();
	private SpelField.String targetPath =  new SpelField.String();
	private Map<String, String> labels = new HashMap<>(); // TODO
	private List<DockerSwarmSecret> dockerSwarmSecrets = new ArrayList(); // TODO
	private String dockerRegistryDomain;
	private String dockerRegistryUsername;
	private String dockerRegistryPassword;

	public SpelField.String getImage() {
		return image;
	}
	public void setImage(SpelField.String image) {
		this.image = image;
	}
	public SpelField.StringList getCmd() {
		return cmd;
	}
	public void setCmd(SpelField.StringList cmd) {
		this.cmd = cmd;
	}
	public Map<String, String> getEnv() {
		return env;
	}
	public void setEnv(Map<String, String> env) {
		this.env = env;
	}
	public SpelField.String getEnvFile() {
		return envFile;
	}
	public void setEnvFile(SpelField.String envFile) {
		this.envFile = envFile;
	}
	public SpelField.String getNetwork() {
		return network;
	}
	public void setNetwork(SpelField.String network) {
		this.network = network;
	}
	public SpelField.StringList getNetworkConnections() {
		return networkConnections;
	}
	public void setNetworkConnections(SpelField.StringList networkConnections) {
		this.networkConnections = networkConnections;
	}
	public SpelField.StringList getDns() {
		return dns;
	}
	public void setDns(SpelField.StringList dns) {
		this.dns = dns;
	}
	public SpelField.StringList getVolumes() {
		return volumes;
	}
	public void setVolumes(SpelField.StringList volumes) {
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
	public SpelField.String getMemoryRequest() {
		return memoryRequest;
	}
	public void setMemoryRequest(SpelField.String memoryRequest) {
		this.memoryRequest = memoryRequest;
	}
	public SpelField.String getMemoryLimit() {
		return memoryLimit;
	}
	public void setMemoryLimit(SpelField.String memoryLimit) {
		this.memoryLimit = memoryLimit;
	}
	public SpelField.String getCpuRequest() {
		return cpuRequest;
	}
	public void setCpuRequest(SpelField.String cpuRequest) {
		this.cpuRequest = cpuRequest;
	}
	public SpelField.String getCpuLimit() {
		return cpuLimit;
	}
	public void setCpuLimit(SpelField.String cpuLimit) {
		this.cpuLimit = cpuLimit;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = labels;
	}

	public SpelField.String getTargetPath() {
		return targetPath;
	}

	public void setTargetPath(SpelField.String targetPath) {
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

	public Integer getIndex() {
		return index;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

	public void resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
		image.resolve(resolver, context);
		cmd.resolve(resolver, context);
		envFile.resolve(resolver, context);
		network.resolve(resolver, context);
		networkConnections.resolve(resolver, context);
		dns.resolve(resolver, context);
		volumes.resolve(resolver, context);
		memoryRequest.resolve(resolver, context);
		memoryLimit.resolve(resolver, context);
		cpuRequest.resolve(resolver, context);
		cpuLimit.resolve(resolver, context);
		targetPath.resolve(resolver, context);
	}
}
