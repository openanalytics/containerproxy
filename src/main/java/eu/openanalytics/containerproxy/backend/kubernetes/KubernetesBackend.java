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
package eu.openanalytics.containerproxy.backend.kubernetes;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import org.apache.commons.io.IOUtils;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.util.Retrying;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;

public class KubernetesBackend extends AbstractContainerBackend {

	private static final String PROPERTY_PREFIX = "proxy.kubernetes.";
	
	private static final String PROPERTY_NAMESPACE = "namespace";
	private static final String PROPERTY_API_VERSION = "api-version";
	private static final String PROPERTY_IMG_PULL_POLICY = "image-pull-policy";
	private static final String PROPERTY_IMG_PULL_SECRETS = "image-pull-secrets";
	private static final String PROPERTY_IMG_PULL_SECRET = "image-pull-secret";
	private static final String PROPERTY_NODE_SELECTOR = "node-selector";
	
	private static final String DEFAULT_NAMESPACE = "default";
	private static final String DEFAULT_API_VERSION = "v1";
	
	private static final String PARAM_POD = "pod";
	private static final String PARAM_SERVICE = "service";
	
	private KubernetesClient kubeClient;
	
	@Override
	public void initialize() throws ContainerProxyException {
		super.initialize();
		
		ConfigBuilder configBuilder = new ConfigBuilder();
		
		String masterUrl = getProperty(PROPERTY_URL);
		if (masterUrl != null) configBuilder.withMasterUrl(masterUrl);
		
		String certPath = getProperty(PROPERTY_CERT_PATH);
		if (certPath != null && Files.isDirectory(Paths.get(certPath))) {
			Path certFilePath = Paths.get(certPath, "ca.pem");
			if (Files.exists(certFilePath)) configBuilder.withCaCertFile(certFilePath.toString());
			certFilePath = Paths.get(certPath, "cert.pem");
			if (Files.exists(certFilePath)) configBuilder.withClientCertFile(certFilePath.toString());
			certFilePath = Paths.get(certPath, "key.pem");
			if (Files.exists(certFilePath)) configBuilder.withClientKeyFile(certFilePath.toString());
		}
		
		kubeClient = new DefaultKubernetesClient(configBuilder.build());
	}

	@Override
	protected Container startContainer(ContainerSpec spec, Proxy proxy) throws Exception {
		Container container = new Container();
		container.setSpec(spec);
		container.setId(UUID.randomUUID().toString());
		
		String kubeNamespace = getProperty(PROPERTY_NAMESPACE, DEFAULT_NAMESPACE);
		String apiVersion = getProperty(PROPERTY_API_VERSION, DEFAULT_API_VERSION);
		
		String[] volumeStrings = Optional.ofNullable(spec.getVolumes()).orElse(new String[] {});
		List<Volume> volumes = new ArrayList<>();
		VolumeMount[] volumeMounts = new VolumeMount[volumeStrings.length];
		for (int i = 0; i < volumeStrings.length; i++) {
			String[] volume = volumeStrings[i].split(":");
			String hostSource = volume[0];
			String containerDest = volume[1];
			String name = "shinyproxy-volume-" + i;
			volumes.add(new VolumeBuilder()
					.withNewHostPath(hostSource)
					.withName(name)
					.build());
			volumeMounts[i] = new VolumeMountBuilder()
					.withMountPath(containerDest)
					.withName(name)
					.build();
		}

		List<EnvVar> envVars = new ArrayList<>();
		for (String envString : buildEnv(spec, proxy)) {
			int idx = envString.indexOf('=');
			if (idx == -1) log.warn("Invalid environment variable: " + envString);
			envVars.add(new EnvVar(envString.substring(0, idx), envString.substring(idx + 1), null));
		}
		
		SecurityContext security = new SecurityContextBuilder()
				.withPrivileged(spec.isPrivileged())
				.build();
		
		List<ContainerPort> containerPorts = spec.getPortMapping().values().stream()
				.map(p -> new ContainerPortBuilder().withContainerPort(p).build())
				.collect(Collectors.toList());
		
		ContainerBuilder containerBuilder = new ContainerBuilder()
				.withImage(spec.getImage())
				.withCommand(spec.getCmd())
				.withName("sp-container-" + container.getId())
				.withPorts(containerPorts)
				.withVolumeMounts(volumeMounts)
				.withSecurityContext(security)
				.withEnv(envVars);

		String imagePullPolicy = getProperty(PROPERTY_IMG_PULL_POLICY);
		if (imagePullPolicy != null) containerBuilder.withImagePullPolicy(imagePullPolicy);

		String[] imagePullSecrets = {};
		String imagePullSecret = getProperty(PROPERTY_IMG_PULL_SECRET);
		if (imagePullSecret == null) {
			String imagePullSecretArray = getProperty(PROPERTY_IMG_PULL_SECRETS);
			if (imagePullSecretArray != null) imagePullSecrets = imagePullSecretArray.split(",");
		} else {
			imagePullSecrets = new String[] { imagePullSecret };
		}
		
		DoneablePod doneablePod = kubeClient.pods().inNamespace(kubeNamespace).createNew()
				.withApiVersion(apiVersion)
				.withKind("Pod")
				.withNewMetadata()
					.withName("sp-pod-" + container.getId())
					.addToLabels("app", container.getId())
					.endMetadata();
		
		PodSpec podSpec = new PodSpec();
		podSpec.setContainers(Collections.singletonList(containerBuilder.build()));
		podSpec.setVolumes(volumes);
		podSpec.setImagePullSecrets(Arrays.stream(imagePullSecrets)
				.map(LocalObjectReference::new).collect(Collectors.toList()));
		
		String nodeSelectorString = getProperty(PROPERTY_NODE_SELECTOR);
		if (nodeSelectorString != null) {
			podSpec.setNodeSelector(Splitter.on(",").withKeyValueSeparator("=").split(nodeSelectorString));
		}
		
		Pod pod = doneablePod.withSpec(podSpec).done();
		
		pod = kubeClient.resource(pod).waitUntilReady(600, TimeUnit.SECONDS);

		Service service = null;
		if (isUseInternalNetwork()) {
			// If SP runs inside the cluster, it can access pods directly and doesn't need any port publishing service.
		} else {
			List<ServicePort> servicePorts = spec.getPortMapping().values().stream()
					.map(p -> new ServicePortBuilder().withPort(p).build())
					.collect(Collectors.toList());
			
			service = kubeClient.services().inNamespace(kubeNamespace).createNew()
					.withApiVersion(apiVersion)
					.withKind("Service")
					.withNewMetadata()
						.withName("sp-service-" + container.getId())
						.endMetadata()
					.withNewSpec()
						.addToSelector("app", container.getId())
						.withType("NodePort")
						.withPorts(servicePorts)
						.endSpec()
					.done();
			
			// Retry, because if this is done too fast, an 'endpoint not found' exception will be thrown.
			final Service s = service;
			Retrying.retry(i -> {
				try {
					kubeClient.resource(s).waitUntilReady(600, TimeUnit.SECONDS);
					return true;
				} catch (Exception e) {
					return false;
				}
			}, 5, 1000);
			service = kubeClient.resource(service).waitUntilReady(600, TimeUnit.SECONDS);
		}
		
		container.getParameters().put(PARAM_POD, pod);
		container.getParameters().put(PARAM_SERVICE, service);
		
		// Calculate proxy routes for all configured ports.
		for (String mappingKey: spec.getPortMapping().keySet()) {
			int containerPort = spec.getPortMapping().get(mappingKey);
			
			int servicePort = -1;
			if (service != null) servicePort = service.getSpec().getPorts().stream()
					.filter(p -> p.getPort() == containerPort).map(p -> p.getNodePort())
					.findAny().orElse(-1);
			
			String mapping = mappingStrategy.createMapping(mappingKey, container, proxy);
			URI target = calculateTarget(container, containerPort, servicePort);
			proxy.getTargets().put(mapping, target);
		}
		
		Thread.sleep(15000);
		return container;
	}

	protected URI calculateTarget(Container container, int containerPort, int servicePort) throws Exception {
		String targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, DEFAULT_TARGET_PROTOCOL);
		String targetHostName;
		int targetPort;
		
		Pod pod = Pod.class.cast(container.getParameters().get(PARAM_POD));
		
		if (isUseInternalNetwork()) {
			targetHostName = pod.getStatus().getPodIP();
			targetPort = containerPort;
		} else {
			targetHostName = pod.getStatus().getHostIP();
			targetPort = servicePort;
		}
		
		return new URI(String.format("%s://%s:%s", targetProtocol, targetHostName, targetPort));
	}
	
	@Override
	protected void doStopProxy(Proxy proxy) throws Exception {
		for (Container container: proxy.getContainers()) {
			Pod pod = Pod.class.cast(container.getParameters().get(PARAM_POD));
			if (pod != null) kubeClient.pods().delete(pod);
			Service service = Service.class.cast(container.getParameters().get(PARAM_SERVICE));
			if (service != null) kubeClient.services().delete(service);
		}
	}
	
	@Override
	public BiConsumer<OutputStream, OutputStream> getOutputAttacher(Proxy proxy) {
		if (proxy.getContainers().isEmpty()) return null;
		return (stdOut, stdErr) -> {
			try {
				Container container = proxy.getContainers().get(0);
				String kubeNamespace = getProperty(PROPERTY_NAMESPACE, DEFAULT_NAMESPACE);
				LogWatch watcher = kubeClient.pods().inNamespace(kubeNamespace).withName("sp-pod-" + container.getId()).watchLog();
				IOUtils.copy(watcher.getOutput(), stdOut);
			} catch (IOException e) {
				log.error("Error while attaching to container output", e);
			}
		};
	}

	@Override
	protected String getPropertyPrefix() {
		return PROPERTY_PREFIX;
	}

}