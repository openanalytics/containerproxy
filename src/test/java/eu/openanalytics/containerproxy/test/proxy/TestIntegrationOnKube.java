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
package eu.openanalytics.containerproxy.test.proxy;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.kubernetes.KubernetesBackend;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.test.helpers.KubernetesTestBase;
import eu.openanalytics.containerproxy.test.proxy.TestIntegrationOnKube.TestConfiguration;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
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
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Test which tests various setups against a Kubernetes Backend.
 * Things which are not yet tested:
 * - internal networking
 * - node selector
 * - image pull policy + secret
 *
 * How to run these tests:
 * 1. Install minikube on your system
 * 2. run minikube start
 * 3. run tests:
 * - using Eclipse or mvn test
 * - Arquillian will find you Kube config automatically
 * 4. Done. All used resources should be gone in minikube.
 */
@SpringBootTest(classes = {TestConfiguration.class, ContainerProxyApplication.class})
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
@ActiveProfiles("test")
public class TestIntegrationOnKube extends KubernetesTestBase {

    @Inject
    private Environment environment;

    @Inject
    private ProxyService proxyService;

    /**
     * This test starts a Proxy with a very simple Spec and checks whether
     * everything is correctly configured in kube.
     */
    @Test
    public void launchProxy() {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[0].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());
            Assertions.assertFalse(pod.getSpec().getContainers().get(0).getSecurityContext().getPrivileged());

            ServiceList serviceList = client.services().inNamespace(namespace).list();
            Assertions.assertEquals(1, serviceList.getItems().size());
            Service service = serviceList.getItems().get(0);
            Assertions.assertEquals(namespace, service.getMetadata().getNamespace());
            Assertions.assertEquals("sp-service-" + containerId, service.getMetadata().getName());
            Assertions.assertEquals(containerId, service.getSpec().getSelector().get("app"));
            Assertions.assertEquals(1, service.getSpec().getPorts().size());
            Assertions.assertEquals(Integer.valueOf(3838), service.getSpec().getPorts().get(0).getTargetPort().getIntVal());

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());
            // all services should be deleted
            serviceList = client.services().inNamespace(namespace).list();
            Assertions.assertEquals(0, serviceList.getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }

    /**
     * This test starts a Proxy with a Spec containing two volumes.
     */
    @Test
    public void launchProxyWithVolumes() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[1].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            // Check Volumes
            Assertions.assertTrue(pod.getSpec().getVolumes().size() >= 2); // at least two volumes should be created
            Assertions.assertEquals("shinyproxy-volume-0", pod.getSpec().getVolumes().get(0).getName());
            Assertions.assertEquals("/srv/myvolume1", pod.getSpec().getVolumes().get(0).getHostPath().getPath());
            Assertions.assertEquals("", pod.getSpec().getVolumes().get(0).getHostPath().getType());
            Assertions.assertEquals("shinyproxy-volume-1", pod.getSpec().getVolumes().get(1).getName());
            Assertions.assertEquals("/srv/myvolume2", pod.getSpec().getVolumes().get(1).getHostPath().getPath());
            Assertions.assertEquals("", pod.getSpec().getVolumes().get(1).getHostPath().getType());

            // Check Volume mounts
            List<VolumeMount> volumeMounts = pod.getSpec().getContainers().get(0).getVolumeMounts();
            Assertions.assertTrue(volumeMounts.size() >= 2); // at least two volume mounts should be created
            Assertions.assertEquals("shinyproxy-volume-0", volumeMounts.get(0).getName());
            Assertions.assertEquals("/srv/myvolume1", volumeMounts.get(0).getMountPath());
            Assertions.assertEquals("shinyproxy-volume-1", volumeMounts.get(1).getName());
            Assertions.assertEquals("/srv/myvolume2", volumeMounts.get(1).getMountPath());

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }

    /**
     * This test starts a Proxy with a Spec containing env variables.
     */
    @Test
    public void launchProxyWithEnv() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[2].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            // Check Env Variables
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
            Assertions.assertTrue(env.containsKey("SHINYPROXY_USERNAME"));
            Assertions.assertEquals("jack", env.get("SHINYPROXY_USERNAME").getValue()); // value is a String "null"
            Assertions.assertTrue(env.containsKey("SHINYPROXY_USERGROUPS"));
            Assertions.assertNull(env.get("SHINYPROXY_USERGROUPS").getValue());
            Assertions.assertTrue(env.containsKey("VAR1"));
            Assertions.assertNull(env.get("VAR1").getValue());
            Assertions.assertTrue(env.containsKey("VAR2"));
            Assertions.assertEquals("VALUE2", env.get("VAR2").getValue());
            Assertions.assertTrue(env.containsKey("VAR3"));
            Assertions.assertEquals("VALUE3", env.get("VAR3").getValue());

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }

    @Test
    public void launchProxyWithSecretRef() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            // first create the required secret
            client.secrets().inNamespace(namespace).create(
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

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            // Check Env Variables
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
            Assertions.assertTrue(env.containsKey("MY_SECRET"));
            Assertions.assertEquals("username", env.get("MY_SECRET").getValueFrom().getSecretKeyRef().getKey());
            Assertions.assertEquals("mysecret", env.get("MY_SECRET").getValueFrom().getSecretKeyRef().getName());

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }

    @Test
    public void launchProxyWithResources() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[4].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            // Check Resource limits/requests
            ResourceRequirements req = pod.getSpec().getContainers().get(0).getResources();
            Assertions.assertEquals(new Quantity("1"), req.getRequests().get("cpu"));
            Assertions.assertEquals(new Quantity("1Gi"), req.getRequests().get("memory"));
            Assertions.assertEquals(new Quantity("2"), req.getLimits().get("cpu"));
            Assertions.assertEquals(new Quantity("2Gi"), req.getLimits().get("memory"));

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }

    @Test
    public void launchProxyWithPrivileged() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[5].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            Assertions.assertTrue(pod.getSpec().getContainers().get(0).getSecurityContext().getPrivileged());

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }

    @Test
    public void launchProxyWithPodPatches() {
        setup((client, namespace, overriddenNamespace) -> {
            final String serviceAccountName = "sp-ittest-b9fa0a24-account";
            try {
                client.serviceAccounts().inNamespace(overriddenNamespace).create(new ServiceAccountBuilder()
                        .withNewMetadata()
                        .withName(serviceAccountName)
                        .withNamespace(overriddenNamespace)
                        .endMetadata()
                        .build());

                // Give Kube time to setup ServiceAccount
                Thread.sleep(2000);

                String specId = environment.getProperty("proxy.specs[6].id");

                ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
                ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
                Proxy proxy = proxyService.startProxy(spec, true);
                String containerId = proxy.getContainers().get(0).getId();

                // Check whether the effectively used namepsace is correct
                Assertions.assertEquals(overriddenNamespace, proxy.getContainers().get(0).getParameters().get("namespace").toString());
                // no pods should exists in the default namespace
                PodList podList = client.pods().inNamespace(namespace).list();
                Assertions.assertEquals(0, podList.getItems().size());

                // no services should exists in the default namespace
                ServiceList serviceList = client.services().inNamespace(namespace).list();
                Assertions.assertEquals(0, serviceList.getItems().size());

                podList = client.pods().inNamespace(overriddenNamespace).list();
                Assertions.assertEquals(1, podList.getItems().size());
                Pod pod = podList.getItems().get(0);
                Assertions.assertEquals("Running", pod.getStatus().getPhase());
                Assertions.assertEquals(overriddenNamespace, pod.getMetadata().getNamespace());
                Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
                Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
                Assertions.assertEquals(serviceAccountName, pod.getSpec().getServiceAccount());
                ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
                Assertions.assertEquals(true, container.getReady());
                Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

                serviceList = client.services().inNamespace(overriddenNamespace).list();
                Assertions.assertEquals(1, serviceList.getItems().size());
                Service service = serviceList.getItems().get(0);
                Assertions.assertEquals(overriddenNamespace, service.getMetadata().getNamespace());
                Assertions.assertEquals("sp-service-" + containerId, service.getMetadata().getName());
                Assertions.assertEquals(containerId, service.getSpec().getSelector().get("app"));
                Assertions.assertEquals(1, service.getSpec().getPorts().size());
                Assertions.assertEquals(Integer.valueOf(3838), service.getSpec().getPorts().get(0).getTargetPort().getIntVal());

                proxyService.stopProxy(proxy, false, true);

                // Give Kube the time to clean
                Thread.sleep(2000);

                // all pods should be deleted
                podList = client.pods().inNamespace(overriddenNamespace).list();
                Assertions.assertEquals(0, podList.getItems().size());

                // all services should be deleted
                serviceList = client.services().inNamespace(overriddenNamespace).list();
                Assertions.assertEquals(0, serviceList.getItems().size());

                Assertions.assertEquals(0, proxyService.getProxies(null, true).size());

            } finally {
                client.serviceAccounts().withName(serviceAccountName).delete();
            }
        });
    }

    /**
     * Test whether the merging of properties works properly
     */
    @Test
    public void launchProxyWithPatchesWithMerging() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[7].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            // Check volumes: one HostPath is added using SP and one using patches
            Assertions.assertTrue(pod.getSpec().getVolumes().size() >= 2); // at least two volumes should be created
            Assertions.assertEquals("shinyproxy-volume-0", pod.getSpec().getVolumes().get(1).getName());
            Assertions.assertEquals("/srv/myvolume1", pod.getSpec().getVolumes().get(1).getHostPath().getPath());
            Assertions.assertEquals("", pod.getSpec().getVolumes().get(1).getHostPath().getType());
            Assertions.assertEquals("cache-volume", pod.getSpec().getVolumes().get(0).getName());
            Assertions.assertNotNull(pod.getSpec().getVolumes().get(0).getEmptyDir());

            // Check Env Variables
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
            Assertions.assertTrue(env.containsKey("VAR1"));
            Assertions.assertEquals("VALUE1", env.get("VAR1").getValue()); // value is a String "null"
            Assertions.assertTrue(env.containsKey("ADDED_VAR"));
            Assertions.assertEquals("VALUE", env.get("ADDED_VAR").getValue()); // value is a String "null"

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }

    /**
     * Tests the creation and deleting of additional manifests.
     * The first manifest contains a namespace definition.
     * The second manifest does not contain a namespace definition, but in the end should have the same namespace as the pod.
     */
    @Test
    public void launchProxyWithAdditionalManifests() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[8].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(overriddenNamespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            PersistentVolumeClaimList claimList = client.persistentVolumeClaims().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(1, claimList.getItems().size());
            PersistentVolumeClaim claim = claimList.getItems().get(0);
            Assertions.assertEquals(overriddenNamespace, claim.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-pvc", claim.getMetadata().getName());

            // secret has no namespace defined -> should be created in the namespace defined by the pod patches
            SecretList sercetList = client.secrets().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(2, sercetList.getItems().size());
            for (Secret secret : sercetList.getItems()) {
                if (secret.getMetadata().getName().startsWith("default-token")) {
                    continue;
                }
                Assertions.assertEquals(overriddenNamespace, secret.getMetadata().getNamespace());
                Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            }

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());
            // all additional manifests should be deleted
            Assertions.assertEquals(1, client.secrets().inNamespace(overriddenNamespace).list().getItems().size());
            Assertions.assertTrue(client.secrets().inNamespace(overriddenNamespace).list()
                    .getItems().get(0).getMetadata().getName().startsWith("default-token"));
            Assertions.assertEquals(0, client.persistentVolumeClaims().inNamespace(overriddenNamespace).list().getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
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
        setup((client, namespace, overriddenNamespace) -> {
            // create the PVC
            String pvcSpec =
                    "apiVersion: v1\n" +
                            "kind: PersistentVolumeClaim\n" +
                            "metadata:\n" +
                            "   name: manifests-pvc\n" +
                            "   namespace: itest-overridden\n" +
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

            PodList podList = client.pods().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(overriddenNamespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            PersistentVolumeClaimList claimList = client.persistentVolumeClaims().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(1, claimList.getItems().size());
            PersistentVolumeClaim claim = claimList.getItems().get(0);
            Assertions.assertEquals(overriddenNamespace, claim.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-pvc", claim.getMetadata().getName());

            // secret has no namespace defined -> should be created in the namespace defined by the pod patches
            SecretList sercetList = client.secrets().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(2, sercetList.getItems().size());
            for (Secret secret : sercetList.getItems()) {
                if (secret.getMetadata().getName().startsWith("default-token")) {
                    continue;
                }
                Assertions.assertEquals(overriddenNamespace, secret.getMetadata().getNamespace());
                Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            }

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());
            // all additional manifests should be deleted
            Assertions.assertEquals(1, client.secrets().inNamespace(overriddenNamespace).list().getItems().size());
            Assertions.assertTrue(client.secrets().inNamespace(overriddenNamespace).list()
                    .getItems().get(0).getMetadata().getName().startsWith("default-token"));
            Assertions.assertEquals(0, client.persistentVolumeClaims().inNamespace(overriddenNamespace).list().getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }


    /**
     * Tests the use of Spring Expression in kubernetes patches and additional manifests.
     */
    @Test
    public void launchProxyWithExpressionInPatchAndManifests() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[9].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());


            // check env variables
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
            Assertions.assertTrue(env.containsKey("CUSTOM_USERNAME"));
            Assertions.assertTrue(env.containsKey("PROXY_ID"));
            Assertions.assertEquals("jack", env.get("CUSTOM_USERNAME").getValue());
            Assertions.assertEquals(proxy.getId(), env.get("PROXY_ID").getValue());

            PersistentVolumeClaimList claimList = client.persistentVolumeClaims().inNamespace(namespace).list();
            Assertions.assertEquals(1, claimList.getItems().size());
            PersistentVolumeClaim claim = claimList.getItems().get(0);
            Assertions.assertEquals(namespace, claim.getMetadata().getNamespace());
            Assertions.assertEquals("home-dir-pvc-jack", claim.getMetadata().getName());

            // check volume mount
            Volume volume = pod.getSpec().getVolumes().get(0);
            Assertions.assertEquals("home-dir-pvc-jack", volume.getName());
            Assertions.assertEquals("home-dir-pvc-jack", volume.getPersistentVolumeClaim().getClaimName());

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());
            // all additional manifests should be deleted
            Assertions.assertEquals(0, client.persistentVolumeClaims().inNamespace(namespace).list().getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }

    /**
     * Tests the creation and deleting of additional manifests (both persistent and non-persistent).
     * The first manifest contains a namespace definition and is non-persistent (it is a secret).
     * The second manifest does not contain a namespace definition, but in the end should have the same namespace as the pod.
     * It is a PVC which should be persistent.
     */
    @Test
    public void launchProxyWithAdditionalPersistentManifests() throws Exception {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[11].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(overriddenNamespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());

            PersistentVolumeClaimList claimList = client.persistentVolumeClaims().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(1, claimList.getItems().size());
            PersistentVolumeClaim claim = claimList.getItems().get(0);
            Assertions.assertEquals(overriddenNamespace, claim.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-pvc", claim.getMetadata().getName());

            // secret has no namespace defined -> should be created in the namespace defined by the pod patches
            SecretList sercetList = client.secrets().inNamespace(overriddenNamespace).list();
            Assertions.assertEquals(2, sercetList.getItems().size());
            for (Secret secret : sercetList.getItems()) {
                if (secret.getMetadata().getName().startsWith("default-token")) {
                    continue;
                }
                Assertions.assertEquals(overriddenNamespace, secret.getMetadata().getNamespace());
                Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            }

            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());
            // the secret should be deleted
            Assertions.assertEquals(1, client.secrets().inNamespace(overriddenNamespace).list().getItems().size());
            Assertions.assertTrue(client.secrets().inNamespace(overriddenNamespace).list()
                    .getItems().get(0).getMetadata().getName().startsWith("default-token"));

            // the PVC should not be deleted
            Assertions.assertEquals(1, client.persistentVolumeClaims().inNamespace(overriddenNamespace).list().getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
    }


    /**
     * This test starts a Proxy with an advanced setup of RuntimeLabels and environment variables + labels.
     */
    @Test
    public void advancedRuntimeLabels() {
        setup((client, namespace, overriddenNamespace) -> {
            String specId = environment.getProperty("proxy.specs[12].id");

            ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);
            Proxy proxy = proxyService.startProxy(spec, true);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + containerId, pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertEquals("openanalytics/shinyproxy-demo:latest", container.getImage());
            Assertions.assertFalse(pod.getSpec().getContainers().get(0).getSecurityContext().getPrivileged());

            ServiceList serviceList = client.services().inNamespace(namespace).list();
            Assertions.assertEquals(1, serviceList.getItems().size());
            Service service = serviceList.getItems().get(0);
            Assertions.assertEquals(namespace, service.getMetadata().getNamespace());
            Assertions.assertEquals("sp-service-" + containerId, service.getMetadata().getName());
            Assertions.assertEquals(containerId, service.getSpec().getSelector().get("app"));
            Assertions.assertEquals(1, service.getSpec().getPorts().size());
            Assertions.assertEquals(Integer.valueOf(3838), service.getSpec().getPorts().get(0).getTargetPort().getIntVal());


            // assert env
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));

            Assertions.assertTrue(env.containsKey("TEST_PROXY_ID"));
            Assertions.assertEquals(proxy.getId(), env.get("TEST_PROXY_ID").getValue());

            Assertions.assertTrue(env.containsKey("SHINYPROXY_USERNAME"));
            Assertions.assertEquals("abc_xyz", env.get("SHINYPROXY_USERNAME").getValue());

            Assertions.assertTrue(env.containsKey("TEST_INSTANCE_ID"));
            Assertions.assertEquals(proxy.getRuntimeValue("SHINYPROXY_INSTANCE"), env.get("TEST_INSTANCE_ID").getValue());

            Assertions.assertTrue(env.containsKey("SHINYPROXY_USERNAME_PATCH"));
            Assertions.assertEquals(proxy.getUserId(), env.get("SHINYPROXY_USERNAME_PATCH").getValue());

            // assert labels
            Map<String, String> labels = pod.getMetadata().getLabels();

            Assertions.assertTrue(labels.containsKey("custom_username_label"));
            Assertions.assertEquals(proxy.getUserId(), labels.get("custom_username_label"));

            Assertions.assertTrue(labels.containsKey("custom_label_patch_instance"));
            Assertions.assertEquals(proxy.getRuntimeValue("SHINYPROXY_INSTANCE"), labels.get("custom_label_patch_instance"));


            proxyService.stopProxy(proxy, false, true);

            // Give Kube the time to clean
            Thread.sleep(2000);

            // all pods should be deleted
            podList = client.pods().inNamespace(namespace).list();
            Assertions.assertEquals(0, podList.getItems().size());
            // all services should be deleted
            serviceList = client.services().inNamespace(namespace).list();
            Assertions.assertEquals(0, serviceList.getItems().size());

            Assertions.assertEquals(0, proxyService.getProxies(null, true).size());
        });
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

        @Bean
        @Primary
        public UserService mockedUserService() {
            return new MockedUserService();
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
