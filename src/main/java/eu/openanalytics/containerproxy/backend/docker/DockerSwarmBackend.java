/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.RegistryAuth;
import com.spotify.docker.client.messages.mount.Mount;
import com.spotify.docker.client.messages.swarm.DnsConfig;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.NetworkAttachmentConfig;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.ResourceRequirements;
import com.spotify.docker.client.messages.swarm.Resources;
import com.spotify.docker.client.messages.swarm.Secret;
import com.spotify.docker.client.messages.swarm.SecretBind;
import com.spotify.docker.client.messages.swarm.SecretFile;
import com.spotify.docker.client.messages.swarm.Service;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.Task;
import com.spotify.docker.client.messages.swarm.TaskSpec;
import eu.openanalytics.containerproxy.ContainerFailedToStartException;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.PortMappings;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.DockerSwarmSecret;
import eu.openanalytics.containerproxy.model.spec.PortMapping;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.Retrying;
import org.springframework.security.core.Authentication;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DockerSwarmBackend extends AbstractDockerBackend {

	@Override
	public void initialize() throws ContainerProxyException {
		super.initialize();
		String swarmId = null;
		try {
			swarmId = dockerClient.inspectSwarm().id();
		} catch (Exception e) {}
		if (swarmId == null) throw new ContainerProxyException("Backend is not a Docker Swarm");
	}

	@Override
	protected Container startContainer(Authentication user, Container initialContainer, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ContainerFailedToStartException  {
		Container.ContainerBuilder rContainerBuilder = initialContainer.toBuilder();
		try {
			Mount[] mounts = spec.getVolumes()
					.mapOrNull(volumes -> volumes.stream()
							.map(b -> b.split(":"))
							.map(fromTo -> Mount.builder().source(fromTo[0]).target(fromTo[1]).type("bind").build())
							.toArray(i -> new Mount[i]));

			Map<String, String> labels = spec.getLabels().getValueOrDefault(new HashMap<>());

			Stream.concat(
					proxy.getRuntimeValues().values().stream(),
					initialContainer.getRuntimeValues().values().stream()
			).forEach(runtimeValue -> {
				if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
					labels.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
				}
			});

			List<SecretBind> secretBinds = new ArrayList<>();
			for (DockerSwarmSecret secret : spec.getDockerSwarmSecrets()) {
				secretBinds.add(convertSecret(secret));
			}

			com.spotify.docker.client.messages.swarm.ContainerSpec containerSpec =
					com.spotify.docker.client.messages.swarm.ContainerSpec.builder()
							.image(spec.getImage().getValue())
							.labels(labels)
							.command(spec.getCmd().getValueOrNull())
							.env(convertEnv(buildEnv(user, spec, proxy)))
							.dnsConfig(DnsConfig.builder().nameServers(spec.getDns().getValueOrNull()).build())
							.mounts(mounts)
							.secrets(secretBinds)
							.build();

			List<NetworkAttachmentConfig> networks = spec.getNetworkConnections()
					.getValueOrDefault(new ArrayList<>())
					.stream()
					.map(n -> NetworkAttachmentConfig.builder().target(n).build())
					.collect(Collectors.toList());

			if (spec.getNetwork().isPresent()) {
				networks.add(NetworkAttachmentConfig.builder().target(spec.getNetwork().getValue()).build());
			}

			Resources.Builder reservationsBuilder = Resources.builder();
			// reservations are used by the Docker swarm scheduler
			if (spec.getCpuRequest().isPresent()) {
				// note: 1 CPU = 1 * 10e8 nanoCpu -> equivalent to --cpus option
				reservationsBuilder.nanoCpus((long) (Double.parseDouble(spec.getCpuRequest().getValue()) * 10e8));
			}
			if (spec.getMemoryRequest().isPresent()) {
				reservationsBuilder.memoryBytes(memoryToBytes(spec.getMemoryRequest().getValue()));
			}

			Resources.Builder limitsBuilder = Resources.builder();
			if (spec.getCpuLimit().isPresent()) {
				// note: 1 CPU = 1 * 10e8 nanoCpu -> equivalent to --cpus option
				limitsBuilder.nanoCpus((long) (Double.parseDouble(spec.getCpuLimit().getValue()) * 10e8));
			}
			if (spec.getMemoryLimit().isPresent()) {
				limitsBuilder.memoryBytes(memoryToBytes(spec.getMemoryLimit().getValue()));
			}

			String serviceName = "sp-service-" + UUID.randomUUID().toString();
			ServiceSpec.Builder serviceSpecBuilder = ServiceSpec.builder()
					.networks(networks)
					.name(serviceName)
					.taskTemplate(TaskSpec.builder()
							.containerSpec(containerSpec)
							.resources(ResourceRequirements.builder()
									.reservations(reservationsBuilder.build())
									.limits(limitsBuilder.build())
									.build())
							.build());

			List<PortConfig> portsToPublish = new ArrayList<>();
			Map<Integer, Integer> portBindings = new HashMap<>();
			if (isUseInternalNetwork()) {
				// In internal networking mode, we can access container ports directly, no need to bind on host.
			} else {
				// Access ports via port publishing on the service.
				for (PortMapping portMapping : spec.getPortMapping()) {
					int hostPort = portAllocator.allocate(portRangeFrom, portRangeTo, proxy.getId());
					portsToPublish.add(PortConfig.builder().publishedPort(hostPort).targetPort(portMapping.getPort()).build());
					portBindings.put(portMapping.getPort(), hostPort);
				}
				serviceSpecBuilder.endpointSpec(EndpointSpec.builder().ports(portsToPublish).build());
			}

			String serviceId;
			if (spec.getDockerRegistryDomain() != null
					&& spec.getDockerRegistryUsername() != null
					&& spec.getDockerRegistryPassword() != null) {

				RegistryAuth registryAuth = RegistryAuth.builder()
						.serverAddress(spec.getDockerRegistryDomain())
						.username(spec.getDockerRegistryUsername())
						.password(spec.getDockerRegistryPassword())
						.build();
				serviceId = dockerClient.createService(serviceSpecBuilder.build(), registryAuth).id();
			} else {
				serviceId = dockerClient.createService(serviceSpecBuilder.build()).id();
			}

			// tell the status service we are starting the container
			proxyStartupLogBuilder.startingContainer(initialContainer.getIndex());
			rContainerBuilder.addRuntimeValue(new RuntimeValue(BackendContainerNameKey.inst, serviceId), false);

			// Give the service some time to start up and launch a container.
			boolean containerFound = Retrying.retry((i, maxAttempts) -> {
				try {
					Task serviceTask = dockerClient
							.listTasks(Task.Criteria.builder().serviceName(serviceName).build())
							.stream().findAny().orElseThrow(() -> new IllegalStateException("Swarm service has no tasks"));
					if (serviceTask.status().containerStatus() != null) {
						rContainerBuilder.id(serviceTask.status().containerStatus().containerId());
						return true;
					}
				} catch (Exception e) {
					throw new RuntimeException("Failed to inspect swarm service tasks", e);
				}
				return false;
			}, 60_000, true);

			if (!containerFound) {
				dockerClient.removeService(serviceId);
				throw new ContainerFailedToStartException("Swarm container did not start in time", null, rContainerBuilder.build());
			}
			proxyStartupLogBuilder.containerStarted(initialContainer.getIndex());

			return setupPortMappingExistingProxy(proxy, rContainerBuilder.build(), portBindings);
		} catch (ContainerFailedToStartException t) {
			throw t;
		} catch (Throwable t) {
			throw new ContainerFailedToStartException("Docker swarm container failed to start", t, rContainerBuilder.build());
		}
	}

	@Override
	protected URI calculateTarget(Container container, PortMappings.PortMappingEntry portMapping, Integer servicePort) throws Exception {
		String targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, DEFAULT_TARGET_PROTOCOL);
		String targetHostName;
		int targetPort;

		if (isUseInternalNetwork()) {
			// Access on containerShortId:containerPort
			targetHostName = container.getId().substring(0, 12);
			targetPort = portMapping.getPort();
		} else {
			// Access on dockerHostName:servicePort
			URL hostURL = new URL(getProperty(PROPERTY_URL, DEFAULT_TARGET_URL));
			targetHostName = hostURL.getHost();
			targetPort = servicePort;
		}

		return new URI(String.format("%s://%s:%s%s", targetProtocol, targetHostName, targetPort, portMapping.getTargetPath()));
	}

    private SecretBind convertSecret(DockerSwarmSecret secret) throws DockerException, InterruptedException {
        if (secret.getName() == null) {
            throw new IllegalArgumentException("No name for a Docker swarm secret provided");
        }
        return SecretBind.builder()
                .secretName(secret.getName())
                .secretId(getSecretId(secret.getName()))
                .file(
                        SecretFile.builder()
                                .name(Optional.ofNullable(secret.getTarget()).orElse(secret.getName()))
                                .gid(Optional.ofNullable(secret.getGid()).orElse("0"))
                                .uid(Optional.ofNullable(secret.getUid()).orElse("0"))
                                .mode(Long.parseLong(Optional.ofNullable(secret.getMode()).orElse("444"), 8))
                                .build()
                )
                .build();

    }

    private String getSecretId(String secretName) throws DockerException, InterruptedException {
        return dockerClient.listSecrets().stream()
                .filter(it -> it.secretSpec().name().equals(secretName))
                .findFirst()
                .map(Secret::id).orElseThrow(() -> new IllegalArgumentException("Secret not found!"));
    }

    @Override
	protected void doStopProxy(Proxy proxy) throws Exception {
		for (Container container: proxy.getContainers()) {
			String serviceId = container.getRuntimeObjectOrNull(BackendContainerNameKey.inst);
			if (serviceId != null) dockerClient.removeService(serviceId);
		}
		portAllocator.release(proxy.getId());
	}

	@Override
	public List<ExistingContainerInfo> scanExistingContainers() throws Exception {
		ArrayList<ExistingContainerInfo> containers = new ArrayList<>();

		for (Service service : dockerClient.listServices()) {
			com.spotify.docker.client.messages.swarm.ContainerSpec containerSpec = service.spec().taskTemplate().containerSpec();

			if (containerSpec == null) {
				continue;
			}

			List<com.spotify.docker.client.messages.Container> containersInService = dockerClient.listContainers(DockerClient.ListContainersParam.withLabel("com.docker.swarm.service.id", service.id()));
			if (containersInService.size() != 1) {
				log.warn(String.format("Found not correct amount of containers for service %s, therefore skipping this", service.id()));
				continue;
			}

			Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseLabelsAsRuntimeValues(containersInService.get(0).id(), containerSpec.labels());
			if (runtimeValues == null) {
				continue;
			}
			runtimeValues.put(ContainerImageKey.inst, new RuntimeValue(ContainerImageKey.inst, containersInService.get(0).image()));
			runtimeValues.put(BackendContainerNameKey.inst, new RuntimeValue(BackendContainerNameKey.inst, service.id()));

			String containerInstanceId = runtimeValues.get(InstanceIdKey.inst).getObject();
			if (!appRecoveryService.canRecoverProxy(containerInstanceId)) {
				log.warn("Ignoring container {} because instanceId {} is not correct", containersInService.get(0).id(), containerInstanceId);
				continue;
			}

			Map<Integer, Integer> portBindings = new HashMap<>();
			if (service.endpoint() != null && service.endpoint().ports() != null){
				for (PortConfig portMapping : service.endpoint().ports()) {
					int hostPort = portMapping.publishedPort();
					int containerPort = portMapping.targetPort();
					portBindings.put(containerPort, hostPort);
					portAllocator.addExistingPort(runtimeValues.get(UserIdKey.inst).getObject(), hostPort);
				}
			}

			containers.add(new ExistingContainerInfo(containersInService.get(0).id(), runtimeValues,
					containerSpec.image(), portBindings));

		}

		return containers;
	}


}
