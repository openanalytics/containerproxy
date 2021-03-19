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

import java.net.URI;
import java.net.URL;
import java.util.*;

import javax.validation.constraints.Null;

import com.spotify.docker.client.messages.RegistryAuth;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DockerSwarmBackend extends AbstractDockerBackend {

	private static final String PARAM_SERVICE_ID = "serviceId";
	private Logger log = LogManager.getLogger(DockerSwarmBackend.class);

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
		Map<String, String> labels = spec.getLabels();
		spec.getRuntimeLabels().forEach((key, value) -> labels.put(key, value.getSecond()));

		com.spotify.docker.client.messages.swarm.ContainerSpec containerSpec = 
				com.spotify.docker.client.messages.swarm.ContainerSpec.builder()
				.image(spec.getImage())
				.labels(labels)
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
		
		// create service with registry auth, if failes fallback to without auth info
		// the end result is docker swarm will pull the image when not avaialbe on disk
		String serviceId;

		// Get container settings for container-auth-domain and container-auth-user and container-auth-password
		// ziyunxiao: there is a betterway to populate ContainerSpec with auth parameter before 
		// calling this function. Hopefully someone more familiar with this project can 
		// rewrite this logic
		List<ContainerSpec> cspecs = proxy.getSpec().getContainerSpecs();				
		String authDomain = null, authUser = null, authPassword = null;
		for(int i=0; i<cspecs.size(); i++){
			ContainerSpec cspec = cspecs.get(i);
			if (cspec.getImage() == spec.getImage()){
				authDomain = cspec.getAuthDomain();
				authUser = cspec.getAuthUser();
				authPassword = cspec.getAuthPassword();
				log.info("image: " + spec.getImage() + ", domain: " + authDomain);
				break;
			}
		}

		if (authDomain != null  && authUser != null && authPassword != null){
			RegistryAuth registryAuth = RegistryAuth.builder()
			.serverAddress(authDomain)
			.username(authUser)
			.password(authPassword)				
			.build();
			try{
				serviceId = dockerClient.createService(serviceSpecBuilder.build(), registryAuth).id();
			}catch (Exception ex){
				log.error("Not able to create service with authentication.");
				log.error(ex);
				// fallback to original logic, in case any error.
				// there is a bug that the project docker-clinet is depending on jersey-common version 2.22
				// but somehow shinyproxy the end output of jersey-common version is 2.30 
				// which org.glassfish.jersey.internal.util.Base64 is removed.
				serviceId = dockerClient.createService(serviceSpecBuilder.build()).id();
			}
			
		} else{
			serviceId = dockerClient.createService(serviceSpecBuilder.build()).id();
		}
		
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
