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
package eu.openanalytics.containerproxy.backend.kubernetes;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.util.Retrying;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
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
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import io.fabric8.kubernetes.client.utils.Serialization;

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
	private static final String PARAM_NAMESPACE = "namespace";
	
	private static final String SECRET_KEY_REF = "secretKeyRef";
	
	private static final String LABEL_PROXIED_APP = "openanalytics.eu/containerproxy-proxied-app";
	private static final String LABEL_INSTANCE = "openanalytics.eu/sp-instance";
		
	
	@Inject
	private PodPatcher podPatcher;
	
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
		try {
			log.info("Hash of config is: " + getInstanceId());
		} catch(Exception e) {
			throw new RuntimeException("Cannot compute hash of config", e);
		}
	}

	public void initialize(KubernetesClient client) {
		kubeClient = client;
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
					.withNewHostPath(hostSource, "")
					.withName(name)
					.build());
			volumeMounts[i] = new VolumeMountBuilder()
					.withMountPath(containerDest)
					.withName(name)
					.build();
		}

		List<EnvVar> envVars = new ArrayList<>();
		for (String envString : buildEnv(spec, proxy)) {
			String[] e = envString.split("=");
			if (e.length == 1) e = new String[] { e[0], "" };
			if (e.length > 2) e[1] = envString.substring(envString.indexOf('=') + 1);
			
			if (e[1].toLowerCase().startsWith(SECRET_KEY_REF.toLowerCase())) {
				String[] ref = e[1].split(":");
				if (ref.length != 3) {
					log.warn(String.format("Invalid secret key reference: %s. Expected format: '%s:<name>:<key>'", envString, SECRET_KEY_REF));
					continue;
				}
				envVars.add(new EnvVar(e[0], null, new EnvVarSourceBuilder()
						.withSecretKeyRef(new SecretKeySelectorBuilder()
								.withName(ref[1])
								.withKey(ref[2])
								.build())
						.build()));
			} else {
				envVars.add(new EnvVar(e[0], e[1], null));
			}
		}
		
		SecurityContext security = new SecurityContextBuilder()
				.withPrivileged(isPrivileged() || spec.isPrivileged())
				.build();
		
		ResourceRequirementsBuilder resourceRequirementsBuilder = new ResourceRequirementsBuilder();
		resourceRequirementsBuilder.addToRequests("cpu", Optional.ofNullable(spec.getCpuRequest()).map(s -> new Quantity(s)).orElse(null));
		resourceRequirementsBuilder.addToLimits("cpu", Optional.ofNullable(spec.getCpuLimit()).map(s -> new Quantity(s)).orElse(null));
		resourceRequirementsBuilder.addToRequests("memory", Optional.ofNullable(spec.getMemoryRequest()).map(s -> new Quantity(s)).orElse(null));
		resourceRequirementsBuilder.addToLimits("memory", Optional.ofNullable(spec.getMemoryLimit()).map(s -> new Quantity(s)).orElse(null));
		
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
				.withResources(resourceRequirementsBuilder.build())
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
		
		PodBuilder podBuilder = new PodBuilder()
				.withApiVersion(apiVersion)
				.withKind("Pod")
				.withNewMetadata()
					.withNamespace(kubeNamespace)
					.withName("sp-pod-" + container.getId())
					.addToLabels(spec.getLabels())
					.addToLabels("app", container.getId())
					.addToLabels(LABEL_INSTANCE, getInstanceId())
					.addToLabels(LABEL_PROXIED_APP, "true")
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
		
		Pod startupPod = podBuilder.withSpec(podSpec).build();
		Pod patchedPod = podPatcher.patchWithDebug(startupPod, proxy.getSpec().getKubernetesPodPatchAsJsonpatch());
		final String effectiveKubeNamespace = patchedPod.getMetadata().getNamespace(); // use the namespace of the patched Pod, in case the patch changes the namespace.
		container.getParameters().put(PARAM_NAMESPACE, effectiveKubeNamespace);
		
		// create additional manifests -> use the effective (i.e. patched) namespace if no namespace is provided
		createAdditionalManifstes(proxy, effectiveKubeNamespace);
		
		Pod startedPod = kubeClient.pods().inNamespace(effectiveKubeNamespace).create(patchedPod);
		
		int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.kubernetes.pod-wait-time", "60000"));
		int maxTries = totalWaitMs / 1000;
		Retrying.retry(i -> {
				if (!Readiness.isReady(kubeClient.resource(startedPod).fromServer().get())) {
					if (i > 1 && log != null) log.debug(String.format("Container not ready yet, trying again (%d/%d)", i, maxTries));
					return false;
				}
				return true;
			}
		, maxTries, 1000);
		if (!Readiness.isReady(kubeClient.resource(startedPod).fromServer().get())) {
			throw new ContainerProxyException("Container did not become ready in time");
		}
		Pod pod = kubeClient.resource(startedPod).fromServer().get();
		
		Service service = null;
		if (isUseInternalNetwork()) {
			// If SP runs inside the cluster, it can access pods directly and doesn't need any port publishing service.
		} else {
			List<ServicePort> servicePorts = spec.getPortMapping().values().stream()
					.map(p -> new ServicePortBuilder().withPort(p).build())
					.collect(Collectors.toList());
			
			Service startupService = kubeClient.services().inNamespace(effectiveKubeNamespace).createNew()
					.withApiVersion(apiVersion)
					.withKind("Service")
					.withNewMetadata()
						.withName("sp-service-" + container.getId())
						.addToLabels(LABEL_INSTANCE, getInstanceId())
						.addToLabels(LABEL_PROXIED_APP, "true")
						.endMetadata()
					.withNewSpec()
						.addToSelector("app", container.getId())
						.withType("NodePort")
						.withPorts(servicePorts)
						.endSpec()
					.done();

			// Workaround: waitUntilReady appears to be buggy.
			Retrying.retry(i -> isServiceReady(kubeClient.resource(startupService).fromServer().get()), 60, 1000);
			
			service = kubeClient.resource(startupService).fromServer().get();
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
		
		
		return container;
	}
	
	
	/**
	 * Creates the extra manifests/resources defined in the ProxySpec.
	 * 
	 * The resource will only be created if it does not already exist.
	 */
	private void createAdditionalManifstes(Proxy proxy, String namespace) {
		for (HasMetadata fullObject: getAdditionManifestsAsObjects(proxy, namespace)) {
			if (kubeClient.resource(fullObject).fromServer().get() == null) {
				kubeClient.resource(fullObject).createOrReplace();
			}
		}
	}

	/**
	 * Converts the additional manifests of the spec into HasMetadat objects.
	 * When the resource has no namespace definition, the provided namespace
	 * parameter will be used.
	 */
	private List<HasMetadata> getAdditionManifestsAsObjects(Proxy proxy, String namespace) {
		ArrayList<HasMetadata> result = new ArrayList<HasMetadata>();
		for (String manifest : proxy.getSpec().getKubernetesAdditionalManifests()) {
			HasMetadata object = Serialization.unmarshal(new ByteArrayInputStream(manifest.getBytes())); // used to determine whether the manifest has specified a namespace

			HasMetadata fullObject = kubeClient.load(new ByteArrayInputStream(manifest.getBytes())).get().get(0);
			if (object.getMetadata().getNamespace() == null) {
				// the load method (in some cases) automatically sets a namepsace when no namespace is provided
				// therefore we overwrite this namespace with the namsepace of the pod.
				fullObject.getMetadata().setNamespace(namespace);
			}
			result.add(fullObject);
		}
		return result;
	}
	
	private boolean isServiceReady(Service service) {
		if (service == null) {
			return false;
		}
		if (service.getStatus() == null) {
			return false;
		}
		if (service.getStatus().getLoadBalancer() == null) {
			return false;
		}
		
		return true;
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
			String kubeNamespace = container.getParameters().get(PARAM_NAMESPACE).toString();
			if (kubeNamespace == null) {
				kubeNamespace = getProperty(PROPERTY_NAMESPACE, DEFAULT_NAMESPACE);
			}
			
			Pod pod = Pod.class.cast(container.getParameters().get(PARAM_POD));
			if (pod != null) kubeClient.pods().inNamespace(kubeNamespace).delete(pod);
			Service service = Service.class.cast(container.getParameters().get(PARAM_SERVICE));
			if (service != null) kubeClient.services().inNamespace(kubeNamespace).delete(service);

			// delete additional manifests
			for (HasMetadata fullObject: getAdditionManifestsAsObjects(proxy, kubeNamespace)) {
				kubeClient.resource(fullObject).delete();
			}
		}
	}
	
	@Override
	public BiConsumer<OutputStream, OutputStream> getOutputAttacher(Proxy proxy) {
		if (proxy.getContainers().isEmpty()) return null;
		return (stdOut, stdErr) -> {
			try {
				Container container = proxy.getContainers().get(0);
				String kubeNamespace = container.getParameters().get(PARAM_NAMESPACE).toString();
				if (kubeNamespace == null) {
					kubeNamespace = getProperty(PROPERTY_NAMESPACE, DEFAULT_NAMESPACE);
				}
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
	
	private String instanceId = null;
	
	/**
	 * Calculates a hash of the config file (i.e. application.yaml).
	 */
	private String getInstanceId() throws JsonParseException, JsonMappingException, IOException, NoSuchAlgorithmException {
		if (instanceId != null) {
			return instanceId;
		}
		
		/**
		 * We need a hash of some "canonical" version of the config file.
		 * The hash should not change when e.g. comments are added to the file.
		 * Therefore we read the application.yml file into an Object and then 
		 * dump it again into YAML. We also sort the keys of maps and properties so that
		 * the order does not matter for the resulting hash.
		 */
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        
		File file = Paths.get(ContainerProxyApplication.CONFIG_FILENAME).toFile();
		if (!file.exists()) {
			file = Paths.get(ContainerProxyApplication.CONFIG_DEMO_PROFILE).toFile();
		}
		if (!file.exists()) {
			// this should only happen in tests
			instanceId = "unknown-instance-id";
			return instanceId;
		}

		Object parsedConfig = objectMapper.readValue(file, Object.class);
		String canonicalConfigFile =  objectMapper.writeValueAsString(parsedConfig);
		
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
		digest.reset();
		digest.update(canonicalConfigFile.getBytes(Charsets.UTF_8));
		instanceId = String.format("%040x", new BigInteger(1, digest.digest()));
		return instanceId;
	}


}