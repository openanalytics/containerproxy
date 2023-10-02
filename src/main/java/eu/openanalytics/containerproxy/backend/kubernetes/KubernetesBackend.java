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
package eu.openanalytics.containerproxy.backend.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.google.common.base.Splitter;
import eu.openanalytics.containerproxy.ContainerFailedToStartException;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.PortMappings;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxiedAppKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.Retrying;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.util.Pair;
import org.springframework.security.core.Authentication;

import javax.inject.Inject;
import javax.json.JsonPatch;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.openanalytics.containerproxy.backend.kubernetes.PodPatcher.DEBUG_PROPERTY;

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

	private static final String SECRET_KEY_REF = "secretKeyRef";

	private static final String ANNOTATION_MANIFEST_POLICY = "openanalytics.eu/sp-additional-manifest-policy";

	@Inject
	private PodPatcher podPatcher;

	private KubernetesClient kubeClient;

	private KubernetesManifestsRemover kubernetesManifestsRemover;

	private Boolean logManifests;

	private final ObjectMapper writer = new ObjectMapper(new YAMLFactory());

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
		kubernetesManifestsRemover = new KubernetesManifestsRemover(kubeClient, getAppNamespaces(), identifierService);
		logManifests = environment.getProperty(DEBUG_PROPERTY, Boolean.class, false);
	}

	public void initialize(KubernetesClient client) {
		super.initialize();
		kubeClient = client;
		kubernetesManifestsRemover = new KubernetesManifestsRemover(kubeClient, getAppNamespaces(), identifierService);
		logManifests = environment.getProperty(DEBUG_PROPERTY, Boolean.class, false);
	}

	@Override
	protected Container startContainer(Authentication user, Container initialContainer, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ContainerFailedToStartException {
		Container.ContainerBuilder rContainerBuilder = initialContainer.toBuilder();
		String containerId = UUID.randomUUID().toString();
		rContainerBuilder.id(containerId);

		KubernetesSpecExtension specExtension = proxySpec.getSpecExtension(KubernetesSpecExtension.class);
		try {
			String kubeNamespace = getProperty(PROPERTY_NAMESPACE, DEFAULT_NAMESPACE);
			String apiVersion = getProperty(PROPERTY_API_VERSION, DEFAULT_API_VERSION);

			List<String> volumeStrings = spec.getVolumes().getValueOrDefault(Collections.emptyList());
			List<Volume> volumes = new ArrayList<>();
			VolumeMount[] volumeMounts = new VolumeMount[volumeStrings.size()];
			for (int i = 0; i < volumeStrings.size(); i++) {
				String[] volume = volumeStrings.get(i).split(":");
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
			for (Map.Entry<String, String> envVar : buildEnv(user, spec, proxy).entrySet()) {
				if (envVar.getValue().toLowerCase().startsWith(SECRET_KEY_REF.toLowerCase())) {
					String[] ref = envVar.getValue().split(":");
					if (ref.length != 3) {
						slog.warn( proxy, String.format("Invalid secret key reference: %s=%s. Expected format: '%s:<name>:<key>'", envVar.getKey(), envVar.getValue(), SECRET_KEY_REF));
						continue;
					}
					envVars.add(new EnvVar(envVar.getKey(), null, new EnvVarSourceBuilder()
							.withSecretKeyRef(new SecretKeySelectorBuilder()
									.withName(ref[1])
									.withKey(ref[2])
									.build())
							.build()));
				} else {
					envVars.add(new EnvVar(envVar.getKey(), envVar.getValue(), null));
				}
			}

			SecurityContext security = new SecurityContextBuilder()
					.withPrivileged(isPrivileged() || spec.isPrivileged())
					.build();

			ResourceRequirementsBuilder resourceRequirementsBuilder = new ResourceRequirementsBuilder();
			resourceRequirementsBuilder.addToRequests("cpu", spec.getCpuRequest().mapOrNull(Quantity::new));
			resourceRequirementsBuilder.addToLimits("cpu", spec.getCpuLimit().mapOrNull(Quantity::new));
			resourceRequirementsBuilder.addToRequests("memory", spec.getMemoryRequest().mapOrNull(Quantity::new));
			resourceRequirementsBuilder.addToLimits("memory", spec.getMemoryLimit().mapOrNull(Quantity::new));

			List<ContainerPort> containerPorts = spec.getPortMapping().stream()
					.map(p -> new ContainerPortBuilder().withContainerPort(p.getPort()).build())
					.collect(Collectors.toList());

			ContainerBuilder containerBuilder = new ContainerBuilder()
					.withImage(spec.getImage().getValue())
					.withCommand(spec.getCmd().getValueOrNull())
					.withName("sp-container-" + containerId)
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
				imagePullSecrets = new String[]{imagePullSecret};
			}

			Map<String, String> serviceLabels = new HashMap<>();
			Map<String, String> podLabels = new HashMap<>();
			podLabels.put("app", containerId);

			ObjectMetaBuilder objectMetaBuilder = new ObjectMetaBuilder()
					.withNamespace(kubeNamespace)
					.withName("sp-pod-" + containerId);

			Stream.concat(
					proxy.getRuntimeValues().values().stream(),
					initialContainer.getRuntimeValues().values().stream()
			).forEach(runtimeValue -> {
				if (runtimeValue.getKey().getIncludeAsLabel()) {
					podLabels.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
					serviceLabels.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
				}
				if (runtimeValue.getKey().getIncludeAsAnnotation()) {
					objectMetaBuilder.addToAnnotations(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
				}
			});

			if (spec.getLabels().isPresent()) {
				podLabels.putAll(spec.getLabels().getValue());
			}

			objectMetaBuilder.addToLabels(podLabels);

			PodBuilder podBuilder = new PodBuilder()
					.withApiVersion(apiVersion)
					.withKind("Pod")
					.withMetadata(objectMetaBuilder.build());

			PodSpec podSpec = new PodSpec();
			podSpec.setContainers(Collections.singletonList(containerBuilder.build()));
			podSpec.setVolumes(volumes);
			podSpec.setImagePullSecrets(Arrays.stream(imagePullSecrets)
					.map(LocalObjectReference::new).collect(Collectors.toList()));

			String nodeSelectorString = getProperty(PROPERTY_NODE_SELECTOR);
			if (nodeSelectorString != null) {
				podSpec.setNodeSelector(Splitter.on(",").withKeyValueSeparator("=").split(nodeSelectorString));
			}

			JsonPatch patch = readPatchFromSpec(specExtension);

			Pod startupPod = podBuilder.withSpec(podSpec).build();
			Pod patchedPod = podPatcher.patchWithDebug(proxy, startupPod, patch);
			final String effectiveKubeNamespace = patchedPod.getMetadata().getNamespace(); // use the namespace of the patched Pod, in case the patch changes the namespace.
			// set the BackendContainerName now, so that the pod can be deleted in case other steps of this function fails
			rContainerBuilder.addRuntimeValue(new RuntimeValue(BackendContainerNameKey.inst, effectiveKubeNamespace + "/" + patchedPod.getMetadata().getName()), false);

			// create additional manifests -> use the effective (i.e. patched) namespace if no namespace is provided
			createAdditionalManifests(proxy, specExtension, effectiveKubeNamespace);

			// tell the status service we are starting the pod/container
			proxyStartupLogBuilder.startingContainer(initialContainer.getIndex());

			// create and start the pod
			Pod startedPod = kubeClient.pods().inNamespace(effectiveKubeNamespace).create(patchedPod);

			int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.kubernetes.pod-wait-time", "60000"));
			boolean podReady = Retrying.retry((currentAttempt, maxAttempts) -> {
				if (!Readiness.getInstance().isReady(kubeClient.resource(startedPod).fromServer().get())) {
					if (currentAttempt > 10 && log != null) {
						slog.info(proxy, String.format("Container not ready yet, trying again (%d/%d)", currentAttempt, maxAttempts));
					}
					return false;
				}
				return true;
			}, totalWaitMs);

			if (!podReady) {
				// check a final time whether the pod is ready
				if (!Readiness.getInstance().isReady(kubeClient.resource(startedPod).fromServer().get())) {
					logKubernetesWarnings(proxy, startedPod);
					throw new ContainerFailedToStartException("Container did not become ready in time", null, rContainerBuilder.build());
				}
			}

			proxyStartupLogBuilder.containerStarted(initialContainer.getIndex());
			Pod pod = kubeClient.resource(startedPod).fromServer().get();

			parseKubernetesEvents(spec.getIndex(), pod, proxyStartupLogBuilder);

			Service service = null;
			Map<Integer, Integer> portBindings = new HashMap<>();
			if (isUseInternalNetwork()) {
				// If SP runs inside the cluster, it can access pods directly and doesn't need any port publishing service.
			} else {
				List<ServicePort> servicePorts = spec.getPortMapping().stream()
						.map(p -> new ServicePortBuilder().withPort(p.getPort()).build())
						.collect(Collectors.toList());

				Service startupService = kubeClient.services().inNamespace(effectiveKubeNamespace)
						.create(new ServiceBuilder()
								.withApiVersion(apiVersion)
								.withKind("Service")
								.withNewMetadata()
								.withName("sp-service-" + containerId)
								.withLabels(serviceLabels)
								.endMetadata()
								.withNewSpec()
								.addToSelector("app", containerId)
								.withType("NodePort")
								.withPorts(servicePorts)
								.endSpec()
								.build());

				// Workaround: waitUntilReady appears to be buggy.
				Retrying.retry((currentAttempt, maxAttempts) -> isServiceReady(kubeClient.resource(startupService).fromServer().get()), 60_000);

				service = kubeClient.resource(startupService).fromServer().get();
				portBindings = service.getSpec().getPorts().stream()
						.collect(Collectors.toMap(ServicePort::getPort, ServicePort::getNodePort));
			}

			return setupPortMappingExistingProxy(proxy, rContainerBuilder.build(), portBindings);
		} catch (ContainerFailedToStartException t) {
			throw t;
		} catch (Throwable throwable) {
			throw new ContainerFailedToStartException("Kubernetes container failed to start", throwable, rContainerBuilder.build());
		}
	}

	private void logKubernetesWarnings(Proxy proxy, Pod pod) {
		List<Event> events;
		try {
			events = kubeClient.v1().events().withInvolvedObject(new ObjectReferenceBuilder()
					.withKind("Pod")
					.withName(pod.getMetadata().getName())
					.withNamespace(pod.getMetadata().getNamespace())
					.build()).list().getItems();
		} catch (KubernetesClientException ex) {
			if (ex.getCode() == 403) {
				log.warn("Cannot parse events of pod because of insufficient permissions. Give the ShinyProxy ServiceAccount permission to get events of pods in order to show Kubernetes warnings in ShinyProxy logs.");
				return;
			}
			throw ex;
		}
		for (Event event : events) {
			if (event.getType().equals("Warning")) {
				slog.warn(proxy, "Kubernetes warning: " +  event.getMessage());
			}
		}
	}

	private LocalDateTime getEventTime(Event event) {
		if (event.getEventTime() != null && event.getEventTime().getTime() != null) {
			return ZonedDateTime.parse(event.getEventTime().getTime()).toLocalDateTime();
		}

		if (event.getFirstTimestamp() != null) {
			return ZonedDateTime.parse(event.getFirstTimestamp()).toLocalDateTime();
		}

		if (event.getLastTimestamp() != null) {
			return ZonedDateTime.parse(event.getLastTimestamp()).toLocalDateTime();
		}

		return null;
	}

	private void parseKubernetesEvents(int containerIdx, Pod pod, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) {
		List<Event> events;
		try {
			 events = kubeClient.v1().events().withInvolvedObject(new ObjectReferenceBuilder()
					.withKind("Pod")
					.withName(pod.getMetadata().getName())
					.withNamespace(pod.getMetadata().getNamespace())
					.build()).list().getItems();
		} catch (KubernetesClientException ex) {
			if (ex.getCode() == 403) {
				log.warn("Cannot parse events of pod because of insufficient permissions. If fine-grained statistics are desired, give the ShinyProxy ServiceAccount permission to events of pods.");
				return;
			}
			throw ex;
		}

		LocalDateTime pullingTime = null;
		LocalDateTime pulledTime = null;
		LocalDateTime scheduledTime = null;

		for (Event event : events) {
			if (event.getCount() != null && event.getCount() >  1) {
				// ignore events which happened multiple time as we are unable to properly process them
				continue;
			}
			if (event.getReason().equalsIgnoreCase("Pulling")) {
				pullingTime = getEventTime(event);
			} else if (event.getReason().equalsIgnoreCase("Pulled")) {
				pulledTime = getEventTime(event);
			} else if (event.getReason().equalsIgnoreCase("Scheduled")) {
				scheduledTime = getEventTime(event);
			}
		}

		if (pullingTime != null && pulledTime != null) {
			proxyStartupLogBuilder.imagePulled(containerIdx, pullingTime, pulledTime);
		}

		if (scheduledTime != null) {
			LocalDateTime start = ZonedDateTime.parse(pod.getMetadata().getCreationTimestamp()).toLocalDateTime();
			proxyStartupLogBuilder.containerScheduled(containerIdx, start, scheduledTime);
		}
	}

	private JsonPatch readPatchFromSpec(KubernetesSpecExtension specExtension) throws JsonProcessingException {
		String patchAsString = specExtension.getKubernetesPodPatches();
		if (patchAsString == null || StringUtils.isBlank(patchAsString)) {
			return null;
		}

		ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
		yamlReader.registerModule(new JSR353Module());
		return yamlReader.readValue(patchAsString, JsonPatch.class);
	}

	/**
	 * Creates the extra manifests/resources defined in the ProxySpec.
	 *
	 * The resource will only be created if it does not already exist.
	 */
	private void createAdditionalManifests(Proxy proxy, KubernetesSpecExtension specExtension, String namespace) throws JsonProcessingException {
		for (GenericKubernetesResource fullObject: parseAdditionalManifests(proxy, namespace, specExtension.getKubernetesAdditionalManifests(), false)) {
			applyAdditionalManifest(proxy, fullObject);
		}
		for (GenericKubernetesResource fullObject: parseAdditionalManifests(proxy, namespace, specExtension.getKubernetesAdditionalPersistentManifests(), true)) {
			applyAdditionalManifest(proxy, fullObject);
		}
	}

	private void applyAdditionalManifest(Proxy proxy, GenericKubernetesResource resource) {
		NonNamespaceOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> client
				= kubeClient.genericKubernetesResources(resource.getApiVersion(), resource.getKind()).inNamespace(resource.getMetadata().getNamespace());
		String policy;
		if (resource.getMetadata().getAnnotations() != null) {
			policy = resource.getMetadata().getAnnotations().getOrDefault(ANNOTATION_MANIFEST_POLICY, "CreateOnce");
		} else {
			policy = "CreateOnce";
		}
		if (policy.equalsIgnoreCase("CreateOnce")) {
			if (kubeClient.resource(resource).fromServer().get() == null) {
				client.resource(resource).create();
			}
		} else if (policy.equalsIgnoreCase("Patch")) {
			if (kubeClient.resource(resource).fromServer().get() == null) {
				client.resource(resource).create();
			} else {
				client.withName(resource.getMetadata().getName()).patch(PatchContext.of(PatchType.JSON_MERGE), resource);
			}
		} else if (policy.equalsIgnoreCase("Delete")) {
			if (kubeClient.resource(resource).fromServer().get() != null) {
				kubeClient.resource(resource).withGracePeriod(0).delete();
			}
		} else if (policy.equalsIgnoreCase("Replace")) {
			if (kubeClient.resource(resource).fromServer().get() != null) {
				kubeClient.resource(resource).withGracePeriod(0).delete();
			}
			client.resource(resource).create();
		} else {
			slog.warn(proxy, String.format("Unknown manifest-policy: %s", policy));
		}
	}

	/**
	 * Converts the additional manifests of the spec into HasMetadata objects.
	 * When the resource has no namespace definition, the provided namespace
	 * parameter will be used.
	 */
	private List<GenericKubernetesResource> parseAdditionalManifests(Proxy proxy, String namespace, List<String> manifests, Boolean persistent) throws JsonProcessingException {
		ArrayList<GenericKubernetesResource> result = new ArrayList<>();
		for (String manifest : manifests) {
			GenericKubernetesResource object = Serialization.yamlMapper().readValue(manifest, GenericKubernetesResource.class);

			GenericKubernetesResource fullObject = kubeClient
					.genericKubernetesResources(object.getApiVersion(), object.getKind())
					.load(new ByteArrayInputStream(manifest.getBytes())).get();
			
			if (object.getMetadata().getNamespace() == null) {
				// the load method (in some cases) automatically sets a namespace when no namespace is provided
				// therefore we overwrite this namespace with the namespace of the pod.
				fullObject.getMetadata().setNamespace(namespace);
			}
			if (fullObject.getMetadata().getLabels() == null) {
				fullObject.getMetadata().setLabels(new HashMap<>());
			}
			fullObject.getMetadata().getLabels().put("openanalytics.eu/sp-additional-manifest", "true");
			fullObject.getMetadata().getLabels().put("openanalytics.eu/sp-persistent-manifest", persistent.toString());
			fullObject.getMetadata().getLabels().put("openanalytics.eu/sp-manifest-id", kubernetesManifestsRemover.getManifestId(proxy.getSpecId(), proxy.getUserId()));

			if (logManifests) {
				slog.info(proxy, "Creating additional manifest: \n" + writer.writeValueAsString(fullObject));
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

	protected URI calculateTarget(Container container, PortMappings.PortMappingEntry portMapping, Integer servicePort) throws Exception {
		String targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, DEFAULT_TARGET_PROTOCOL);
		String targetHostName;
		int targetPort;

		Pod pod = getPod(container).orElseThrow(() -> new ContainerFailedToStartException("Pod not found while calculating target", null, container));

		if (isUseInternalNetwork()) {
			targetHostName = pod.getStatus().getPodIP();
			targetPort = portMapping.getPort();
		} else {
			targetHostName = pod.getStatus().getHostIP();
			targetPort = servicePort;
		}

		return new URI(String.format("%s://%s:%s%s", targetProtocol, targetHostName, targetPort, portMapping.getTargetPath()));
	}

	@Override
	protected void doStopProxy(Proxy proxy) throws Exception {
		for (Container container: proxy.getContainers()) {
			Optional<Pair<String, String>> podInfo = getPodInfo(container);
			if (!podInfo.isPresent()) {
				// container was not yet fully created
				continue;
			}

			// specify gracePeriod 0, this was the default in previous version of the fabric8 k8s client
			kubeClient.pods().inNamespace(podInfo.get().getFirst()).withName(podInfo.get().getSecond()).withGracePeriod(0).delete();

			if (!isUseInternalNetwork()) {
				// delete service when not using internal network
				Service service = kubeClient.services().inNamespace(podInfo.get().getFirst()).withName(getServiceName(container)).get();
				if (service != null) {
					kubeClient.resource(service).withGracePeriod(0).delete();
				}
			}
			
			// delete additional manifests
			kubernetesManifestsRemover.deleteAdditionalManifests(proxy.getSpecId(), proxy.getUserId());
		}
	}

	@Override
	public BiConsumer<OutputStream, OutputStream> getOutputAttacher(Proxy proxy) {
		if (proxy.getContainers().isEmpty()) return null;
		return (stdOut, stdErr) -> {
			LogWatch watcher = null;
			try {
				Container container = proxy.getContainers().get(0);
				Optional<Pair<String, String>> pod = getPodInfo(container);
				if (pod.isPresent()) {
						watcher = kubeClient.pods().inNamespace(pod.get().getFirst()).withName(pod.get().getSecond()).watchLog();
						IOUtils.copy(watcher.getOutput(), stdOut);
				} else {
					slog.warn(proxy, "Error while attaching to container output: pod info not found");
				}
			} catch (ClosedChannelException ignored) {
			} catch (IOException e) {
				slog.error(proxy, e, "Error while attaching to container output");
			} finally {
				if (watcher != null) {
					watcher.close();
				}
			}
		};
	}

	@Override
	protected String getPropertyPrefix() {
		return PROPERTY_PREFIX;
	}

	@Override
	public List<ExistingContainerInfo> scanExistingContainers() {
        HashSet<String> namespaces = getAppNamespaces();

		log.debug("Looking for existing pods in namespaces {}", namespaces);

		ArrayList<ExistingContainerInfo> containers = new ArrayList<>();

		for (String namespace : namespaces) {
            List<Pod> pods = kubeClient.pods().inNamespace(namespace)
					.withLabel(ProxiedAppKey.inst.getKeyAsLabel(), "true")
					.list().getItems();

			for (Pod pod : pods) {
				Map<String, String> labels = pod.getMetadata().getLabels();
				Map<String, String> annotations = pod.getMetadata().getAnnotations();

				if (labels == null) {
				    continue;
				}

				String containerId = labels.get("app");
				if (containerId == null) {
					continue; // this isn't a container created by us
				}

				Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseLabelsAndAnnotationsAsRuntimeValues(containerId, labels, annotations);
				if (runtimeValues == null) {
					continue;
				}
				runtimeValues.put(ContainerImageKey.inst, new RuntimeValue(ContainerImageKey.inst, pod.getSpec().getContainers().get(0).getImage()));
				runtimeValues.put(BackendContainerNameKey.inst, new RuntimeValue(BackendContainerNameKey.inst, pod.getMetadata().getNamespace() + "/" + pod.getMetadata().getName()));

				String containerInstanceId = runtimeValues.get(InstanceIdKey.inst).getObject();
				if (!appRecoveryService.canRecoverProxy(containerInstanceId)) {
					log.warn("Ignoring container {} because instanceId {} is not correct", containerId, containerInstanceId);
					continue;
				}

				Map<Integer, Integer> portBindings = new HashMap<>();
				if (!isUseInternalNetwork()) {
					Service service = kubeClient.services().inNamespace(namespace).withName("sp-service-" + containerId).get();
					if (service == null) {
						log.warn("Ignoring container {} because it has no associated service", containerId);
						continue;
					}
					portBindings = service.getSpec().getPorts().stream()
							.collect(Collectors.toMap(ServicePort::getPort, ServicePort::getNodePort));
				}

				containers.add(new ExistingContainerInfo(containerId, runtimeValues,
						pod.getSpec().getContainers().get(0).getImage(),  portBindings));
			}
		}

		return containers;
	}

	private Map<RuntimeValueKey<?>, RuntimeValue> parseLabelsAndAnnotationsAsRuntimeValues(String containerId,
																						Map<String, String> labels,
																						Map<String, String> annotations) {
		Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = new HashMap<>();

		for (RuntimeValueKey<?> key : RuntimeValueKeyRegistry.getRuntimeValueKeys()) {
			if (key.getIncludeAsLabel()) {
				String value = labels.get(key.getKeyAsLabel());
				if (value != null) {
					runtimeValues.put(key, new RuntimeValue(key, key.deserializeFromString(value)));
				}
			} else if (key.getIncludeAsAnnotation() && annotations != null) {
				String value = annotations.get(key.getKeyAsLabel());
				if (value != null) {
					runtimeValues.put(key, new RuntimeValue(key, key.deserializeFromString(value)));
				}
			} else if (key.isRequired()) {
				// value is null but is required
				log.warn("Ignoring container {} because no label or annotation named {} is found", containerId, key.getKeyAsLabel());
				return null;
			}
		}

		return runtimeValues;
	}

	private Optional<Pair<String, String>> getPodInfo(Container container) {
		String podId = container.getRuntimeObjectOrNull(BackendContainerNameKey.inst);
		if (podId == null) {
			return Optional.empty();
		}
		String[] tmp = podId.split("/");
		return Optional.of(Pair.of(tmp[0], tmp[1]));
	}

	private String getServiceName(Container container) {
		return "sp-service-" + container.getId();
	}

	private Optional<Pod> getPod(Container container) {
		return getPodInfo(container).flatMap(this::getPod);
	}

	private Optional<Pod> getPod(Pair<String, String> podInfo) {
		return Optional.ofNullable(kubeClient.pods().inNamespace(podInfo.getFirst()).withName(podInfo.getSecond()).get());
	}

	private HashSet<String> getAppNamespaces() {
		HashSet<String> namespaces = new HashSet<>();
		int i = 0;
		String appNamespace = environment.getProperty(String.format("app-namespaces[%d]", i));
		while (appNamespace != null) {
			namespaces.add(appNamespace);
			i++;
			appNamespace = environment.getProperty(String.format("app-namespaces[%d]", i));
		}
		namespaces.add(getProperty(PROPERTY_NAMESPACE, DEFAULT_NAMESPACE));
		return namespaces;
	}

}
