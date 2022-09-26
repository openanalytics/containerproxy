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
package eu.openanalytics.containerproxy.backend.docker;

import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.NotFoundException;
import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.Container.PortMapping;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.RegistryAuth;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.TargetPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class DockerEngineBackend extends AbstractDockerBackend {

	private static final String PROPERTY_IMG_PULL_POLICY = "image-pull-policy";

	private ImagePullPolicy imagePullPolicy;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void initialize() {
		super.initialize();
		imagePullPolicy = environment.getProperty(getPropertyPrefix() + PROPERTY_IMG_PULL_POLICY, ImagePullPolicy.class, ImagePullPolicy.IfNotPresent);
	}

	@Override
	protected void startContainer(Container container, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec) throws Exception {
		Builder hostConfigBuilder = HostConfig.builder();

		if (imagePullPolicy == ImagePullPolicy.Always
			|| (imagePullPolicy == ImagePullPolicy.IfNotPresent && !isImagePresent(spec))) {
			logger.info("Pulling image {}", spec.getImage());
			proxyStatusService.imagePulling(proxy, container);
			pullImage(spec);
			proxyStatusService.imagePulled(proxy, container);
		}

		Map<String, List<PortBinding>> portBindings = new HashMap<>();
		if (isUseInternalNetwork()) {
			// In internal networking mode, we can access container ports directly, no need to bind on host.
		} else {
			// Allocate ports on the docker host to proxy to.
			for (Integer containerPort: spec.getPortMapping().values()) {
				int hostPort = portAllocator.allocate(proxy.getId());
				portBindings.put(String.valueOf(containerPort), Collections.singletonList(PortBinding.of("0.0.0.0", hostPort)));
			}
		}
		hostConfigBuilder.portBindings(portBindings);
		
		hostConfigBuilder.memoryReservation(memoryToBytes(spec.getMemoryRequest().getValueOrNull()));
		hostConfigBuilder.memory(memoryToBytes(spec.getMemoryLimit().getValueOrNull()));
		if (spec.getCpuLimit().isPresent()) {
			// Workaround, see https://github.com/spotify/docker-client/issues/959
			long period = 100000;
			long quota = (long) (period * Float.parseFloat(spec.getCpuLimit().getValue()));
			hostConfigBuilder.cpuPeriod(period);
			hostConfigBuilder.cpuQuota(quota);
		}

		spec.getNetwork().ifPresent(hostConfigBuilder::networkMode);
		spec.getDns().ifPresent(hostConfigBuilder::dns);
		spec.getVolumes().ifPresent(hostConfigBuilder::binds);
		hostConfigBuilder.privileged(isPrivileged() || spec.isPrivileged());

		Map<String, String> labels = spec.getLabels().getValueOrDefault(new HashMap<>());

		Stream.concat(
				proxy.getRuntimeValues().values().stream(),
				container.getRuntimeValues().values().stream()
		).forEach(runtimeValue -> {
			if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
				labels.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.getValue());
			}
		});

		ContainerConfig containerConfig = ContainerConfig.builder()
			    .hostConfig(hostConfigBuilder.build())
			    .image(spec.getImage().getValue())
			    .labels(labels)
			    .exposedPorts(portBindings.keySet())
			    .cmd(spec.getCmd().getValueOrNull())
			    .env(convertEnv(buildEnv(spec, proxy)))
			    .build();

		try {
			// tell the status service we are starting the container
			proxyStatusService.containerStarting(proxy, container);
			ContainerCreation containerCreation = dockerClient.createContainer(containerConfig);
			container.setId(containerCreation.id());

			if (spec.getNetworkConnections().isPresent()) {
				for (String networkConnection: spec.getNetworkConnections().getValue()) {
					dockerClient.connectToNetwork(containerCreation.id(), networkConnection);
				}
			}

			dockerClient.startContainer(containerCreation.id());
			proxyStatusService.containerStarted(proxy, container);
			container.addRuntimeValue(new RuntimeValue(BackendContainerNameKey.inst, containerCreation.id()));
		} catch (DockerException ex) {
			proxyStatusService.containerStartFailed(proxy, container);
			throw ex;
		}
		// Calculate proxy routes for all configured ports.

		for (String mappingKey: spec.getPortMapping().keySet()) {
			int containerPort = spec.getPortMapping().get(mappingKey);
			
			List<PortBinding> binding = portBindings.get(String.valueOf(containerPort));
			int hostPort = Integer.valueOf(Optional.ofNullable(binding).map(pb -> pb.get(0).hostPort()).orElse("0"));

			String mapping = mappingStrategy.createMapping(mappingKey, container, proxy);
			URI target = calculateTarget(container, containerPort, hostPort);
			proxy.getTargets().put(mapping, target);
		}
	}

	@Override
	protected URI calculateTarget(Container container, int containerPort, int hostPort) throws Exception {
		String targetProtocol;
		String targetHostName;
		String targetPort;
		String targetPath = computeTargetPath(container.getRuntimeValue(TargetPathKey.inst));

		if (isUseInternalNetwork()) {
			targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, DEFAULT_TARGET_PROTOCOL);
			
			// For internal networks, DNS resolution by name only works with custom names.
			// See comments on https://github.com/docker/for-win/issues/1009
			ContainerInfo info = dockerClient.inspectContainer(container.getId());
			targetHostName = info.config().hostname();

			targetPort = String.valueOf(containerPort);
		} else {
			URL hostURL = new URL(getProperty(PROPERTY_URL, DEFAULT_TARGET_URL));
			targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, hostURL.getProtocol());
			targetHostName = hostURL.getHost();
			targetPort = String.valueOf(hostPort);
		}
		
		return new URI(String.format("%s://%s:%s%s", targetProtocol, targetHostName, targetPort, targetPath));
	}
	
	@Override
	protected void doStopProxy(Proxy proxy) throws Exception {
		for (Container container : proxy.getContainers()) {
			ContainerInfo containerInfo = dockerClient.inspectContainer(container.getId());
			if (containerInfo != null && containerInfo.networkSettings() != null
					&& containerInfo.networkSettings().networks() != null) {
				for (AttachedNetwork network : containerInfo.networkSettings().networks().values()) {
					dockerClient.disconnectFromNetwork(container.getId(), network.networkId());
				}
			}
			dockerClient.removeContainer(container.getId(), RemoveContainerParam.forceKill());
		}
		portAllocator.release(proxy.getId());
	}

	@Override
	public List<ExistingContainerInfo> scanExistingContainers() throws Exception {
		ArrayList<ExistingContainerInfo> containers = new ArrayList<>();
		
		for (com.spotify.docker.client.messages.Container container: dockerClient.listContainers(ListContainersParam.allContainers())) {
			if (!container.state().equalsIgnoreCase("running")) {
				continue; // not recovering stopped/broken apps
			}

			Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseLabelsAsRuntimeValues(container.id(), container.labels());
			if (runtimeValues == null) {
				continue;
			}
			runtimeValues.put(ContainerImageKey.inst, new RuntimeValue(ContainerImageKey.inst, container.image()));
			runtimeValues.put(BackendContainerNameKey.inst, new RuntimeValue(BackendContainerNameKey.inst, container.id()));

			// add ports to PortAllocator (even if we don't recover the proxy)
			for (PortMapping portMapping: container.ports()) {
				portAllocator.addExistingPort(runtimeValues.get(UserIdKey.inst).getValue(), portMapping.publicPort());
			}

			String containerInstanceId = runtimeValues.get(InstanceIdKey.inst).getValue();
			if (!appRecoveryService.canRecoverProxy(containerInstanceId)) {
				log.warn("Ignoring container {} because instanceId {} is not correct", container.id(), containerInstanceId);
				continue;
			}

			Map<Integer, Integer> portBindings = new HashMap<>();
			for (PortMapping portMapping: container.ports()) {
				int hostPort = portMapping.publicPort();
				int containerPort = portMapping.privatePort();
				portBindings.put(containerPort, hostPort);
			}	
			
			containers.add(new ExistingContainerInfo(container.id(), runtimeValues, container.image(),  portBindings, new HashMap<>()));

		}
		
		return containers;
	}

	private boolean isImagePresent(ContainerSpec spec) throws DockerException, InterruptedException {
		try {
			dockerClient.inspectImage(spec.getImage().getValue());
			return true;
		} catch (NotFoundException ex) {
			return false;
		}
	}

	private void pullImage(ContainerSpec spec) throws DockerException, InterruptedException {
		if (spec.getDockerRegistryDomain() != null
				&& spec.getDockerRegistryUsername() != null
				&& spec.getDockerRegistryPassword() != null) {

			RegistryAuth registryAuth = RegistryAuth.builder()
					.serverAddress(spec.getDockerRegistryDomain())
					.username(spec.getDockerRegistryUsername())
					.password(spec.getDockerRegistryPassword())
					.build();
			dockerClient.pull(spec.getImage().getValue(), registryAuth, message -> {});
		} else {
			dockerClient.pull(spec.getImage().getValue(), message -> {});
		}
	}

	public enum ImagePullPolicy {
		Never,
		Always,
		IfNotPresent
	}

}
