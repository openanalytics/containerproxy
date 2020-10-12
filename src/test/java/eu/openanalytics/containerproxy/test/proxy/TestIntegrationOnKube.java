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
package eu.openanalytics.containerproxy.test.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.arquillian.cube.kubernetes.api.Session;
import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.arquillian.cube.requirement.ArquillianConditionalRunner;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.kubernetes.KubernetesBackend;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.test.proxy.TestIntegrationOnKube.TestConfiguration;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Test which tests various setups against a Kubernetes Backend.
 * Things which are not yet tested:
 *  - internal networking
 *  - node selector
 *  - image pull policy + secret
 *
 * How to run these tests:
 *  1. Install minikube on your system
 *  2. run minikube start
 *  3. run tests:
 *    - using Eclipse or mvn test
 *    - Arquillian will find you Kube config automatically
 *  4. Done. All used resources should be gone in minikube.
 */
@SpringBootTest(classes = { TestConfiguration.class, ContainerProxyApplication.class })
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
@RunWith(ArquillianConditionalRunner.class)
@ActiveProfiles("test")
@RequiresKubernetes
public class TestIntegrationOnKube {

	@ClassRule
	public static final SpringClassRule springClassRule = new SpringClassRule();

	@Rule
	public final SpringMethodRule springMethodRule = new SpringMethodRule();

	@Inject
	private Environment environment;

	@Inject
	private ProxyService proxyService;

	@ArquillianResource
	public static KubernetesClient client;

	@ArquillianResource
	public static Session session;

	/**
	 * This test starts a Proxy with a very simple Spec and checks whether
	 * everything is correctly configured in kube.
	 */
	@Test
	public void launchProxy() throws Exception {
		String specId = environment.getProperty("proxy.specs[0].id");

		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
		Proxy proxy = proxyService.startProxy(spec, true);
		String containerId = proxy.getContainers().get(0).getId();

		PodList podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(1, podList.getItems().size());
		Pod pod = podList.getItems().get(0);
		assertEquals("Running", pod.getStatus().getPhase());
		assertEquals(session.getNamespace(), pod.getMetadata().getNamespace());
		assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
		assertEquals(1, pod.getStatus().getContainerStatuses().size());
		ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
		assertEquals(true, container.getReady());
		assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());
		assertFalse(pod.getSpec().getContainers().get(0).getSecurityContext().getPrivileged());

		ServiceList serviceList = client.services().inNamespace(session.getNamespace()).list();
		assertEquals(1, serviceList.getItems().size());
		Service service = serviceList.getItems().get(0);
		assertEquals(session.getNamespace(), service.getMetadata().getNamespace());
		assertEquals("sp-service-" + containerId, service.getMetadata().getName());
		assertEquals(containerId, service.getSpec().getSelector().get("app"));
		assertEquals(1, service.getSpec().getPorts().size());
		assertEquals(Integer.valueOf(3838), service.getSpec().getPorts().get(0).getTargetPort().getIntVal());

		proxyService.stopProxy(proxy, false, true);

		// Give Kube the time to clean
		Thread.sleep(2000);

		// all pods should be deleted
		podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(0, podList.getItems().size());
		// all services should be deleted
		serviceList = client.services().inNamespace(session.getNamespace()).list();
		assertEquals(0, serviceList.getItems().size());

		assertEquals(0, proxyService.getProxies(null, true).size());
	}

	/**
	 * This test starts a Proxy with a Spec containing two volumes.
	 */
	@Test
	public void launchProxyWithVolumes() throws Exception {
		String specId = environment.getProperty("proxy.specs[1].id");

		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
		Proxy proxy = proxyService.startProxy(spec, true);
		String containerId = proxy.getContainers().get(0).getId();

		PodList podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(1, podList.getItems().size());
		Pod pod = podList.getItems().get(0);
		assertEquals("Running", pod.getStatus().getPhase());
		assertEquals(session.getNamespace(), pod.getMetadata().getNamespace());
		assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
		assertEquals(1, pod.getStatus().getContainerStatuses().size());
		ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
		assertEquals(true, container.getReady());
		assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

		// Check Volumes
		assertTrue(pod.getSpec().getVolumes().size() >= 2); // at least two volumes should be created
		assertEquals("shinyproxy-volume-0", pod.getSpec().getVolumes().get(0).getName());
		assertEquals("/srv/myvolume1", pod.getSpec().getVolumes().get(0).getHostPath().getPath());
		assertEquals("", pod.getSpec().getVolumes().get(0).getHostPath().getType());
		assertEquals("shinyproxy-volume-1", pod.getSpec().getVolumes().get(1).getName());
		assertEquals("/srv/myvolume2", pod.getSpec().getVolumes().get(1).getHostPath().getPath());
		assertEquals("", pod.getSpec().getVolumes().get(1).getHostPath().getType());

		// Check Volume mounts
		List<VolumeMount> volumeMounts = pod.getSpec().getContainers().get(0).getVolumeMounts();
		assertTrue(volumeMounts.size() >= 2); // at least two volume mounts should be created
		assertEquals("shinyproxy-volume-0", volumeMounts.get(0).getName());
		assertEquals("/srv/myvolume1", volumeMounts.get(0).getMountPath());
		assertEquals("shinyproxy-volume-1", volumeMounts.get(1).getName());
		assertEquals("/srv/myvolume2", volumeMounts.get(1).getMountPath());

		proxyService.stopProxy(proxy, false, true);

		// Give Kube the time to clean
		Thread.sleep(2000);

		// all pods should be deleted
		podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(0, podList.getItems().size());

		assertEquals(0, proxyService.getProxies(null, true).size());
	}

	/**
	 * This test starts a Proxy with a Spec containing env variables.
	 */
	@Test
	public void launchProxyWithEnv() throws Exception {
		String specId = environment.getProperty("proxy.specs[2].id");

		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
		Proxy proxy = proxyService.startProxy(spec, true);
		String containerId = proxy.getContainers().get(0).getId();

		PodList podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(1, podList.getItems().size());
		Pod pod = podList.getItems().get(0);
		assertEquals("Running", pod.getStatus().getPhase());
		assertEquals(session.getNamespace(), pod.getMetadata().getNamespace());
		assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
		assertEquals(1, pod.getStatus().getContainerStatuses().size());
		ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
		assertEquals(true, container.getReady());
		assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

		// Check Env Variables
		List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
		Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
		assertTrue(env.containsKey("SHINYPROXY_USERNAME"));
		assertEquals("null", env.get("SHINYPROXY_USERNAME").getValue()); // value is a String "null"
		assertTrue(env.containsKey("SHINYPROXY_USERGROUPS"));
		assertEquals(null, env.get("SHINYPROXY_USERGROUPS").getValue());
		assertTrue(env.containsKey("VAR1"));
		assertEquals(null, env.get("VAR1").getValue());
		assertTrue(env.containsKey("VAR2"));
		assertEquals("VALUE2", env.get("VAR2").getValue());
		assertTrue(env.containsKey("VAR3"));
		assertEquals("VALUE3", env.get("VAR3").getValue());

		proxyService.stopProxy(proxy, false, true);

		// Give Kube the time to clean
		Thread.sleep(2000);

		// all pods should be deleted
		podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(0, podList.getItems().size());

		assertEquals(0, proxyService.getProxies(null, true).size());
	}

	@Test
	public void launchProxyWithSecretRef() throws Exception {
		// first create the required secret

		client.secrets().inNamespace(session.getNamespace()).create(
				new SecretBuilder()
					.withNewMetadata()
						.withName("mysecret")
					.endMetadata()
					.addToData("username", "YWRtaW4=")
					.build());

		String specId = environment.getProperty("proxy.specs[3].id");

		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
		Proxy proxy = proxyService.startProxy(spec, true);
		String containerId = proxy.getContainers().get(0).getId();

		PodList podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(1, podList.getItems().size());
		Pod pod = podList.getItems().get(0);
		assertEquals("Running", pod.getStatus().getPhase());
		assertEquals(session.getNamespace(), pod.getMetadata().getNamespace());
		assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
		assertEquals(1, pod.getStatus().getContainerStatuses().size());
		ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
		assertEquals(true, container.getReady());
		assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

		// Check Env Variables
		List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
		Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
		assertTrue(env.containsKey("MY_SECRET"));
		assertEquals("username", env.get("MY_SECRET").getValueFrom().getSecretKeyRef().getKey());
		assertEquals("mysecret", env.get("MY_SECRET").getValueFrom().getSecretKeyRef().getName());

		proxyService.stopProxy(proxy, false, true);

		// Give Kube the time to clean
		Thread.sleep(2000);

		// all pods should be deleted
		podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(0, podList.getItems().size());

		assertEquals(0, proxyService.getProxies(null, true).size());
	}

	@Test
	public void launchProxyWithResources() throws Exception {
		String specId = environment.getProperty("proxy.specs[4].id");

		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
		Proxy proxy = proxyService.startProxy(spec, true);
		String containerId = proxy.getContainers().get(0).getId();

		PodList podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(1, podList.getItems().size());
		Pod pod = podList.getItems().get(0);
		assertEquals("Running", pod.getStatus().getPhase());
		assertEquals(session.getNamespace(), pod.getMetadata().getNamespace());
		assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
		assertEquals(1, pod.getStatus().getContainerStatuses().size());
		ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
		assertEquals(true, container.getReady());
		assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

		// Check Resource limits/requests
		ResourceRequirements req = pod.getSpec().getContainers().get(0).getResources();
		assertEquals(new Quantity("1"), req.getRequests().get("cpu"));
		assertEquals(new Quantity("1Gi"), req.getRequests().get("memory"));
		assertEquals(new Quantity("2"), req.getLimits().get("cpu"));
		assertEquals(new Quantity("2Gi"), req.getLimits().get("memory"));

		proxyService.stopProxy(proxy, false, true);

		// Give Kube the time to clean
		Thread.sleep(2000);

		// all pods should be deleted
		podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(0, podList.getItems().size());

		assertEquals(0, proxyService.getProxies(null, true).size());
	}

	@Test
	public void launchProxyWithPrivileged() throws Exception {
		String specId = environment.getProperty("proxy.specs[5].id");

		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
		Proxy proxy = proxyService.startProxy(spec, true);
		String containerId = proxy.getContainers().get(0).getId();

		PodList podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(1, podList.getItems().size());
		Pod pod = podList.getItems().get(0);
		assertEquals("Running", pod.getStatus().getPhase());
		assertEquals(session.getNamespace(), pod.getMetadata().getNamespace());
		assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
		assertEquals(1, pod.getStatus().getContainerStatuses().size());
		ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
		assertEquals(true, container.getReady());
		assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

		assertTrue(pod.getSpec().getContainers().get(0).getSecurityContext().getPrivileged());

		proxyService.stopProxy(proxy, false, true);

		// Give Kube the time to clean
		Thread.sleep(2000);

		// all pods should be deleted
		podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(0, podList.getItems().size());

		assertEquals(0, proxyService.getProxies(null, true).size());
	}

	@Test
	public void launchProxyWithPodPatches() throws Exception {
		final String overridenNamespace = "it-b9fa0a24-overriden";
		final String serviceAccountName = "sp-ittest-b9fa0a24-account";
		try {
			client.namespaces().create(new NamespaceBuilder()
					.withNewMetadata()
						.withName(overridenNamespace)
					.endMetadata()
					.build());

			client.serviceAccounts().inNamespace(overridenNamespace).create(new ServiceAccountBuilder()
					.withNewMetadata()
						.withName(serviceAccountName)
						.withNamespace(overridenNamespace)
					.endMetadata()
					.build());

			String specId = environment.getProperty("proxy.specs[6].id");

			ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
			ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
			Proxy proxy = proxyService.startProxy(spec, true);
			String containerId = proxy.getContainers().get(0).getId();

			// Check whether the effectively used namepsace is correct
			assertEquals(overridenNamespace, proxy.getContainers().get(0).getParameters().get("namespace").toString());
			// no pods should exists in the default namespace
			PodList podList = client.pods().inNamespace(session.getNamespace()).list();
			assertEquals(0, podList.getItems().size());

			// no services should exists in the default namespace
			ServiceList serviceList = client.services().inNamespace(session.getNamespace()).list();
			assertEquals(0, serviceList.getItems().size());

			podList = client.pods().inNamespace(overridenNamespace).list();
			assertEquals(1, podList.getItems().size());
			Pod pod = podList.getItems().get(0);
			assertEquals("Running", pod.getStatus().getPhase());
			assertEquals(overridenNamespace, pod.getMetadata().getNamespace());
			assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
			assertEquals(1, pod.getStatus().getContainerStatuses().size());
			assertEquals(serviceAccountName, pod.getSpec().getServiceAccount());
			ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
			assertEquals(true, container.getReady());
			assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

			serviceList = client.services().inNamespace(overridenNamespace).list();
			assertEquals(1, serviceList.getItems().size());
			Service service = serviceList.getItems().get(0);
			assertEquals(overridenNamespace, service.getMetadata().getNamespace());
			assertEquals("sp-service-" + containerId, service.getMetadata().getName());
			assertEquals(containerId, service.getSpec().getSelector().get("app"));
			assertEquals(1, service.getSpec().getPorts().size());
			assertEquals(Integer.valueOf(3838), service.getSpec().getPorts().get(0).getTargetPort().getIntVal());

			proxyService.stopProxy(proxy, false, true);

			// Give Kube the time to clean
			Thread.sleep(2000);

			// all pods should be deleted
			podList = client.pods().inNamespace(overridenNamespace).list();
			assertEquals(0, podList.getItems().size());

			// all services should be deleted
			serviceList = client.services().inNamespace(overridenNamespace).list();
			assertEquals(0, serviceList.getItems().size());

			assertEquals(0, proxyService.getProxies(null, true).size());

		} finally {
			// just to be sure both the namespace and service account are cleaned up
			try {
				client.namespaces().withName(overridenNamespace).delete();
			} catch(Exception e) {

			}
			try {
				client.serviceAccounts().withName(serviceAccountName).delete();
			} catch(Exception e) {

			}
		}
	}

	/**
	 * Test whether the merging of properties works properly
	 */
	@Test
	public void launchProxyWithPatchesWithMerging() throws Exception {
		String specId = environment.getProperty("proxy.specs[7].id");

		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
		Proxy proxy = proxyService.startProxy(spec, true);
		String containerId = proxy.getContainers().get(0).getId();

		PodList podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(1, podList.getItems().size());
		Pod pod = podList.getItems().get(0);
		assertEquals("Running", pod.getStatus().getPhase());
		assertEquals(session.getNamespace(), pod.getMetadata().getNamespace());
		assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
		assertEquals(1, pod.getStatus().getContainerStatuses().size());
		ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
		assertEquals(true, container.getReady());
		assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

		// Check volumes: one HostPath is added using SP and one using patches
		assertTrue(pod.getSpec().getVolumes().size() >= 2); // at least two volumes should be created
		assertEquals("shinyproxy-volume-0", pod.getSpec().getVolumes().get(1).getName());
		assertEquals("/srv/myvolume1", pod.getSpec().getVolumes().get(1).getHostPath().getPath());
		assertEquals("", pod.getSpec().getVolumes().get(1).getHostPath().getType());
		assertEquals("cache-volume", pod.getSpec().getVolumes().get(0).getName());
		assertNotNull(pod.getSpec().getVolumes().get(0).getEmptyDir());

		// Check Env Variables
		List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
		Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
		assertTrue(env.containsKey("VAR1"));
		assertEquals("VALUE1", env.get("VAR1").getValue()); // value is a String "null"
		assertTrue(env.containsKey("ADDED_VAR"));
		assertEquals("VALUE", env.get("ADDED_VAR").getValue()); // value is a String "null"

		proxyService.stopProxy(proxy, false, true);

		// Give Kube the time to clean
		Thread.sleep(2000);

		// all pods should be deleted
		podList = client.pods().inNamespace(session.getNamespace()).list();
		assertEquals(0, podList.getItems().size());

		assertEquals(0, proxyService.getProxies(null, true).size());
	}

	/**
	 * Tests the creation and deleting of additional manifests.
	 * The first manifest contains a namespace definition.
	 * The second manifest does not contain a namespace definition, but in the end should have the same namespace as the pod.
	 */
	@Test
	public void launchProxyWithAdditionalManifests() throws Exception {
		final String overridenNamespace = "it-b9fa0a24-overriden";
		try {
			System.out.println(client);
			client.namespaces().create(new NamespaceBuilder()
					.withNewMetadata()
						.withName(overridenNamespace)
					.endMetadata()
					.build());
			
			String specId = environment.getProperty("proxy.specs[8].id");

			ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
			ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
			Proxy proxy = proxyService.startProxy(spec, true);
			String containerId = proxy.getContainers().get(0).getId();

			PodList podList = client.pods().inNamespace(overridenNamespace).list();
			assertEquals(1, podList.getItems().size());
			Pod pod = podList.getItems().get(0);
			assertEquals("Running", pod.getStatus().getPhase());
			assertEquals(overridenNamespace, pod.getMetadata().getNamespace());
			assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
			assertEquals(1, pod.getStatus().getContainerStatuses().size());
			ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
			assertEquals(true, container.getReady());
			assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());
			
			PersistentVolumeClaimList claimList = client.persistentVolumeClaims().inNamespace(overridenNamespace).list();
			assertEquals(1, claimList.getItems().size());
			PersistentVolumeClaim claim = claimList.getItems().get(0);
			assertEquals(overridenNamespace, claim.getMetadata().getNamespace());
			assertEquals("manifests-pvc", claim.getMetadata().getName());

			// secret has no namespace defined -> should be created in the namespace defined by the pod patches
			SecretList sercetList = client.secrets().inNamespace(overridenNamespace).list();
			assertEquals(2, sercetList.getItems().size());
			for (Secret secret : sercetList.getItems()) {
				if (secret.getMetadata().getName().startsWith("default-token")) {
					continue;
				}
				assertEquals(overridenNamespace, secret.getMetadata().getNamespace());
				assertEquals("manifests-secret", secret.getMetadata().getName());
			}

			proxyService.stopProxy(proxy, false, true);

			// Give Kube the time to clean
			Thread.sleep(2000);

			// all pods should be deleted
			podList = client.pods().inNamespace(session.getNamespace()).list();
			assertEquals(0, podList.getItems().size());
			// all additional manifests should be deleted
			assertEquals(1, client.secrets().inNamespace(overridenNamespace).list().getItems().size());
			assertTrue(client.secrets().inNamespace(overridenNamespace).list()
					.getItems().get(0).getMetadata().getName().startsWith("default-token"));
			assertEquals(0, client.persistentVolumeClaims().inNamespace(overridenNamespace).list().getItems().size());

			assertEquals(0, proxyService.getProxies(null, true).size());
		} finally {
			// just to be sure both the namespace and service account are cleaned up
			client.namespaces().withName(overridenNamespace).delete();
		}
	}
	
	/**
	 * Tests the creation and deleting of additional manifests.
	 * The first manifest contains a namespace definition.
	 * The second manifest does not contain a namespace definition, but in the end should have the same namespace as the pod.
	 * 
	 * This is exactly the same test as the previous one, except that the PVC already exists (and should not be re-created).
	 */
	@Test
	public void launchProxyWithAdditionalManifestsOfWhichOneAlreadyExists() throws Exception {
		final String overridenNamespace = "it-b9fa0a24-overriden";
		try {
			System.out.println(client);
			client.namespaces().create(new NamespaceBuilder()
					.withNewMetadata()
						.withName(overridenNamespace)
					.endMetadata()
					.build());
			
			// create the PVC
			String pvcSpec = 
					"apiVersion: v1\n" + 
					"kind: PersistentVolumeClaim\n" + 
					"metadata:\n" + 
					"   name: manifests-pvc\n" + 
					"   namespace: it-b9fa0a24-overriden\n" + 
					"spec:\n" + 
					"   storageClassName: standard\n" + 
					"   accessModes:\n" + 
					"     - ReadWriteOnce\n" + 
					"   resources:\n" + 
					"      requests:\n" + 
					"          storage: 5Gi";
			
			client.load(new ByteArrayInputStream(pvcSpec.getBytes())).createOrReplace();
			
			String specId = environment.getProperty("proxy.specs[8].id");

			ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
			ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
			Proxy proxy = proxyService.startProxy(spec, true);
			String containerId = proxy.getContainers().get(0).getId();

			PodList podList = client.pods().inNamespace(overridenNamespace).list();
			assertEquals(1, podList.getItems().size());
			Pod pod = podList.getItems().get(0);
			assertEquals("Running", pod.getStatus().getPhase());
			assertEquals(overridenNamespace, pod.getMetadata().getNamespace());
			assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
			assertEquals(1, pod.getStatus().getContainerStatuses().size());
			ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
			assertEquals(true, container.getReady());
			assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());
			
			PersistentVolumeClaimList claimList = client.persistentVolumeClaims().inNamespace(overridenNamespace).list();
			assertEquals(1, claimList.getItems().size());
			PersistentVolumeClaim claim = claimList.getItems().get(0);
			assertEquals(overridenNamespace, claim.getMetadata().getNamespace());
			assertEquals("manifests-pvc", claim.getMetadata().getName());

			// secret has no namespace defined -> should be created in the namespace defined by the pod patches
			SecretList sercetList = client.secrets().inNamespace(overridenNamespace).list();
			assertEquals(2, sercetList.getItems().size());
			for (Secret secret : sercetList.getItems()) {
				if (secret.getMetadata().getName().startsWith("default-token")) {
					continue;
				}
				assertEquals(overridenNamespace, secret.getMetadata().getNamespace());
				assertEquals("manifests-secret", secret.getMetadata().getName());
			}

			proxyService.stopProxy(proxy, false, true);

			// Give Kube the time to clean
			Thread.sleep(2000);

			// all pods should be deleted
			podList = client.pods().inNamespace(session.getNamespace()).list();
			assertEquals(0, podList.getItems().size());
			// all additional manifests should be deleted
			assertEquals(1, client.secrets().inNamespace(overridenNamespace).list().getItems().size());
			assertTrue(client.secrets().inNamespace(overridenNamespace).list()
					.getItems().get(0).getMetadata().getName().startsWith("default-token"));
			assertEquals(0, client.persistentVolumeClaims().inNamespace(overridenNamespace).list().getItems().size());

			assertEquals(0, proxyService.getProxies(null, true).size());
		} finally {
			// just to be sure both the namespace and service account are cleaned up
			client.namespaces().withName(overridenNamespace).delete();
		}
	}

	public static class TestConfiguration {
		@Bean
		@Primary
		public ProxyMappingManager mappingManager() {
			return new NoopMappingManager();
		}

		@Bean
		@Primary
		public AbstractFactoryBean<IContainerBackend> backendFactory() {
			return new TestContainerBackendFactory();
		}

	}

	public static class TestContainerBackendFactory extends AbstractFactoryBean<IContainerBackend>
			implements ApplicationContextAware {

		private ApplicationContext applicationContext;

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.applicationContext = applicationContext;
		}

		@Override
		public Class<?> getObjectType() {
			return IContainerBackend.class;
		}

		@Override
		protected IContainerBackend createInstance() throws Exception {
			KubernetesBackend backend = new KubernetesBackend();
			applicationContext.getAutowireCapableBeanFactory().autowireBean(backend);
			backend.initialize(client);
			return backend;
		}
	}

	public static class NoopMappingManager extends ProxyMappingManager {
		@Override
		public synchronized void addMapping(String proxyId, String path, URI target) {
			// No-op
		}

		@Override
		public synchronized void removeMapping(String path) {
			// No-ops
		}
	}
}
