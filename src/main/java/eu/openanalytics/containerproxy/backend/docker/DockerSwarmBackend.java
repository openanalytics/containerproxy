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
package eu.openanalytics.containerproxy.backend.docker;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.spotify.docker.client.messages.mount.Mount;
import com.spotify.docker.client.messages.swarm.DnsConfig;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.NetworkAttachmentConfig;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.Task;
import com.spotify.docker.client.messages.swarm.TaskSpec;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.util.Retrying;

public class DockerSwarmBackend extends AbstractDockerBackend {

	private static final String PARAM_SERVICE_ID = "serviceId";

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
	protected Container startContainer(ContainerSpec spec, Proxy proxy) throws Exception {
		Container container = new Container();
		container.setSpec(spec);
		
		Mount[] mounts = null;
		if (spec.getVolumes() != null) mounts = Arrays.stream(spec.getVolumes())
				.map(b -> b.split(":"))
				.map(fromTo -> Mount.builder().source(fromTo[0]).target(fromTo[1]).type("bind").build())
				.toArray(i -> new Mount[i]);

		com.spotify.docker.client.messages.swarm.ContainerSpec containerSpec = 
				com.spotify.docker.client.messages.swarm.ContainerSpec.builder()
				.image(spec.getImage())
				.labels(spec.getLabels())
				.command(spec.getCmd())
				.env(buildEnv(spec, proxy))
				.dnsConfig(DnsConfig.builder().nameServers(spec.getDns()).build())
				.mounts(mounts)
				.build();

		NetworkAttachmentConfig[] networks = Arrays
				.stream(Optional.ofNullable(spec.getNetworkConnections()).orElse(new String[0]))
				.map(n -> NetworkAttachmentConfig.builder().target(n).build())
				.toArray(i -> new NetworkAttachmentConfig[i]);

		if (spec.getNetwork() != null) {
			networks = Arrays.copyOf(networks, networks.length + 1);
			networks[networks.length - 1] = NetworkAttachmentConfig.builder().target(spec.getNetwork()).build();
		}
		
		String serviceName = "sp-service-" + UUID.randomUUID().toString();
		ServiceSpec.Builder serviceSpecBuilder = ServiceSpec.builder()
				.networks(networks)
				.name(serviceName)
				.taskTemplate(TaskSpec.builder()
						.containerSpec(containerSpec)
						.build());
		
		List<PortConfig> portsToPublish = new ArrayList<>();
		if (isUseInternalNetwork()) {
			// In internal networking mode, we can access container ports directly, no need to bind on host.
		} else {
			// Access ports via port publishing on the service.
			for (Integer containerPort: spec.getPortMapping().values()) {
				int hostPort = portAllocator.allocate(proxy.getId());
				portsToPublish.add(PortConfig.builder().publishedPort(hostPort).targetPort(containerPort).build());
			}
			serviceSpecBuilder.endpointSpec(EndpointSpec.builder().ports(portsToPublish).build());
		}
		
		String serviceId = dockerClient.createService(serviceSpecBuilder.build()).id();
		container.getParameters().put(PARAM_SERVICE_ID, serviceId);
		
		// Give the service some time to start up and launch a container.
		boolean containerFound = Retrying.retry(i -> {
			try {
				Task serviceTask = dockerClient
						.listTasks(Task.Criteria.builder().serviceName(serviceName).build())
						.stream().findAny().orElseThrow(() -> new IllegalStateException("Swarm service has no tasks"));
				container.setId(serviceTask.status().containerStatus().containerId());
			} catch (Exception e) {
				throw new RuntimeException("Failed to inspect swarm service tasks", e);
			}
			return (container.getId() != null);
		}, 30, 2000, true);
		
		if (!containerFound) {
			dockerClient.removeService(serviceId);
			throw new IllegalStateException("Swarm container did not start in time");
		}
		
		// Calculate proxy routes for all configured ports.
		for (String mappingKey: spec.getPortMapping().keySet()) {
			int containerPort = spec.getPortMapping().get(mappingKey);
			
			int servicePort = portsToPublish.stream()
					.filter(pc -> pc.targetPort() == containerPort)
					.mapToInt(pc -> pc.publishedPort()).findAny().orElse(-1);
			
			String mapping = mappingStrategy.createMapping(mappingKey, container, proxy);
			URI target = calculateTarget(container, containerPort, servicePort);
			proxy.getTargets().put(mapping, target);
		}
		
		return container;
	}

	protected URI calculateTarget(Container container, int containerPort, int servicePort) throws Exception {
		String targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, DEFAULT_TARGET_PROTOCOL);
		String targetHostName;
		int targetPort;
		
		if (isUseInternalNetwork()) {
			// Access on containerShortId:containerPort
			targetHostName = container.getId().substring(0, 12);
			targetPort = containerPort;
		} else {
			// Access on dockerHostName:servicePort
			URL hostURL = new URL(getProperty(PROPERTY_URL, DEFAULT_TARGET_URL));
			targetHostName = hostURL.getHost();
			targetPort = servicePort;
		}
		
		return new URI(String.format("%s://%s:%s", targetProtocol, targetHostName, targetPort));
	}
	
	@Override
	protected void doStopProxy(Proxy proxy) throws Exception {
		for (Container container: proxy.getContainers()) {
			String serviceId = (String) container.getParameters().get(PARAM_SERVICE_ID);
			if (serviceId != null) dockerClient.removeService(serviceId);
		}
		portAllocator.release(proxy.getId());
	}

}
