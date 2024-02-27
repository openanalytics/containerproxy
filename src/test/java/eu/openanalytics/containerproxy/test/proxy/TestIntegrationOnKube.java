/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.expression.SpelException;
import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import eu.openanalytics.containerproxy.test.helpers.TestUtil;
import eu.openanalytics.containerproxy.util.Retrying;
import io.fabric8.kubernetes.api.model.ConfigMap;
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
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TestIntegrationOnKube {

    private static final ShinyProxyInstance inst = new ShinyProxyInstance("application-test-kubernetes.yml");

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    /**
     * This test starts a Proxy with a very simple Spec and checks whether
     * everything is correctly configured in kube.
     */
    @Test
    public void launchProxy() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello");
            Proxy proxy = inst.proxyService.getProxy(id);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));
            Assertions.assertFalse(pod.getSpec().getContainers().get(0).getSecurityContext().getPrivileged());

            ServiceList serviceList = k8s.client.services().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, serviceList.getItems().size());
            Service service = serviceList.getItems().get(0);
            Assertions.assertEquals(k8s.namespace, service.getMetadata().getNamespace());
            Assertions.assertEquals("sp-service-" + containerId, service.getMetadata().getName());
            Assertions.assertEquals(containerId, service.getSpec().getSelector().get("app"));
            Assertions.assertEquals(1, service.getSpec().getPorts().size());
            Assertions.assertEquals(Integer.valueOf(3838), service.getSpec().getPorts().get(0).getTargetPort().getIntVal());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // all services should be deleted
            assertNoServices(k8s);
        }
    }

    /**
     * This test starts a Proxy with a Spec containing two volumes.
     */
    @Test
    public void launchProxyWithVolumes() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_volume");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

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

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    /**
     * This test starts a Proxy with a Spec containing env variables.
     */
    @Test
    public void launchProxyWithEnv() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_env");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // Check Env Variables
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
            Assertions.assertTrue(env.containsKey("SHINYPROXY_USERNAME"));
            Assertions.assertEquals("demo", env.get("SHINYPROXY_USERNAME").getValue()); // value is a String "null"
            Assertions.assertTrue(env.containsKey("SHINYPROXY_USERGROUPS"));
            Assertions.assertEquals("GROUP1,GROUP2", env.get("SHINYPROXY_USERGROUPS").getValue());
            Assertions.assertTrue(env.containsKey("VAR1"));
            Assertions.assertNull(env.get("VAR1").getValue());
            Assertions.assertTrue(env.containsKey("VAR2"));
            Assertions.assertEquals("VALUE2", env.get("VAR2").getValue());
            Assertions.assertTrue(env.containsKey("VAR3"));
            Assertions.assertEquals("VALUE3", env.get("VAR3").getValue());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithSecretRef() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // first create the required secret
            k8s.client.secrets().inNamespace(k8s.namespace).create(
                new SecretBuilder().withNewMetadata().withName("mysecret").endMetadata().addToData("username", "YWRtaW4=").build());

            String id = inst.client.startProxy("01_hello_secret");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // Check Env Variables
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
            Assertions.assertTrue(env.containsKey("MY_SECRET"));
            Assertions.assertEquals("username", env.get("MY_SECRET").getValueFrom().getSecretKeyRef().getKey());
            Assertions.assertEquals("mysecret", env.get("MY_SECRET").getValueFrom().getSecretKeyRef().getName());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithResources() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_limits");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // Check Resource limits/requests
            ResourceRequirements req = pod.getSpec().getContainers().get(0).getResources();
            Assertions.assertEquals(new Quantity("1"), req.getRequests().get("cpu"));
            Assertions.assertEquals(new Quantity("1Gi"), req.getRequests().get("memory"));
            Assertions.assertEquals(new Quantity("2"), req.getLimits().get("cpu"));
            Assertions.assertEquals(new Quantity("2Gi"), req.getLimits().get("memory"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithPrivileged() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_priv");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            Assertions.assertTrue(pod.getSpec().getContainers().get(0).getSecurityContext().getPrivileged());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithPodPatches() {
        final String serviceAccountName = "sp-ittest-b9fa0a24-account";
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            try {
                k8s.client.serviceAccounts().inNamespace(k8s.overriddenNamespace).create(new ServiceAccountBuilder()
                    .withNewMetadata()
                    .withName(serviceAccountName).withNamespace(k8s.overriddenNamespace)
                    .endMetadata()
                    .build());

                // Give Kube time to setup ServiceAccount
                TestUtil.sleep(2000);
                String id = inst.client.startProxy("01_hello_patches1");
                Proxy proxy = inst.proxyService.getProxy(id);
                String containerId = proxy.getContainers().get(0).getId();

                // Check whether the effectively used namespace is correct
                Assertions.assertEquals(k8s.overriddenNamespace, proxy.getContainers().get(0).getRuntimeValue(BackendContainerNameKey.inst).split("/")[0]);
                // no pods should exist in the default namespace
                PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
                Assertions.assertEquals(0, podList.getItems().size());

                // no services should exist in the default namespace
                ServiceList serviceList = k8s.client.services().inNamespace(k8s.namespace).list();
                Assertions.assertEquals(0, serviceList.getItems().size());

                podList = k8s.client.pods().inNamespace(k8s.overriddenNamespace).list();
                Assertions.assertEquals(1, podList.getItems().size());
                Pod pod = podList.getItems().get(0);
                Assertions.assertEquals("Running", pod.getStatus().getPhase());
                Assertions.assertEquals(k8s.overriddenNamespace, pod.getMetadata().getNamespace());
                Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
                Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
                Assertions.assertEquals(serviceAccountName, pod.getSpec().getServiceAccount());
                ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
                Assertions.assertEquals(true, container.getReady());
                Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

                serviceList = k8s.client.services().inNamespace(k8s.overriddenNamespace).list();
                Assertions.assertEquals(1, serviceList.getItems().size());
                Service service = serviceList.getItems().get(0);
                Assertions.assertEquals(k8s.overriddenNamespace, service.getMetadata().getNamespace());
                Assertions.assertEquals("sp-service-" + containerId, service.getMetadata().getName());
                Assertions.assertEquals(containerId, service.getSpec().getSelector().get("app"));
                Assertions.assertEquals(1, service.getSpec().getPorts().size());
                Assertions.assertEquals(Integer.valueOf(3838), service.getSpec().getPorts().get(0).getTargetPort().getIntVal());

                inst.proxyService.stopProxy(null, proxy, true).run();

                // all pods should be deleted
                assertNoPods(k8s);

                // all services should be deleted
                assertNoServices(k8s);
            } finally {
                k8s.client.serviceAccounts().withName(serviceAccountName).delete();
            }
        }
    }

    /**
     * Test whether the merging of properties works properly
     */
    @Test
    public void launchProxyWithPatchesWithMerging() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_patches2");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

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

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    /**
     * Tests the creation and deleting of additional manifests.
     * The first manifest contains a namespace definition.
     * The second manifest does not contain a namespace definition, but in the end should have the same namespace as the pod.
     */
    @Test
    public void launchProxyWithAdditionalManifests() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_manifests");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.overriddenNamespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.overriddenNamespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            PersistentVolumeClaimList claimList = k8s.client.persistentVolumeClaims().inNamespace(k8s.overriddenNamespace).list();
            Assertions.assertEquals(1, claimList.getItems().size());
            PersistentVolumeClaim claim = claimList.getItems().get(0);
            Assertions.assertEquals(k8s.overriddenNamespace, claim.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-pvc", claim.getMetadata().getName());

            // secret has no namespace defined -> should be created in the namespace defined by the pod patches
            Secret secret = k8s.getSingleSecret(k8s.overriddenNamespace);
            Assertions.assertEquals(k8s.overriddenNamespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);

            // all additional manifests should be deleted
            assertNoSecrets(k8s);
            assertNoPvcs(k8s);
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
    public void launchProxyWithAdditionalManifestsOfWhichOneAlreadyExists() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // create the PVC
            String pvcSpec =
                """
                    apiVersion: v1
                    kind: PersistentVolumeClaim
                    metadata:
                       name: manifests-pvc
                       namespace: itest-overridden
                    spec:
                       storageClassName: standard
                       accessModes:
                         - ReadWriteOnce
                       resources:
                          requests:
                              storage: 5Gi""";


            k8s.client.inNamespace(k8s.overriddenNamespace).load(new ByteArrayInputStream(pvcSpec.getBytes())).createOrReplace();

            String id = inst.client.startProxy("01_hello_manifests");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.overriddenNamespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.overriddenNamespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            PersistentVolumeClaimList claimList = k8s.client.persistentVolumeClaims().inNamespace(k8s.overriddenNamespace).list();
            Assertions.assertEquals(1, claimList.getItems().size());
            PersistentVolumeClaim claim = claimList.getItems().get(0);
            Assertions.assertEquals(k8s.overriddenNamespace, claim.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-pvc", claim.getMetadata().getName());
            // IMPORTANT: PVC should not have any labels, since it is the already existing resource and not created by shinyproxy
            Assertions.assertEquals(0, claim.getMetadata().getLabels().size());

            // secret has no namespace defined -> should be created in the namespace defined by the pod patches
            Secret secret = k8s.getSingleSecret(k8s.overriddenNamespace);
            Assertions.assertEquals(k8s.overriddenNamespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);

            // the secret additional manifests should be deleted
            assertNoSecrets(k8s);
            // the pvc should still exist, shinyproxy does not delete it because it was not created by shinyproxy
            // using sp-additional-manifest-policy: Patch it will be patched and deleted, see test launchProxyWithManifestPolicyPatch case 2
            Assertions.assertEquals(1, k8s.client.persistentVolumeClaims().inNamespace(k8s.overriddenNamespace).list().getItems().size());
        }
    }


    /**
     * Tests the use of Spring Expression in kubernetes patches and additional manifests.
     */
    @Test
    public void launchProxyWithExpressionInPatchAndManifests() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_manifests_espression");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));


            // check env variables
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));
            Assertions.assertTrue(env.containsKey("CUSTOM_USERNAME"));
            Assertions.assertTrue(env.containsKey("PROXY_ID"));
            Assertions.assertEquals("demo", env.get("CUSTOM_USERNAME").getValue());
            Assertions.assertEquals(proxy.getId(), env.get("PROXY_ID").getValue());

            PersistentVolumeClaimList claimList = k8s.client.persistentVolumeClaims().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, claimList.getItems().size());
            PersistentVolumeClaim claim = claimList.getItems().get(0);
            Assertions.assertEquals(k8s.namespace, claim.getMetadata().getNamespace());
            Assertions.assertEquals("home-dir-pvc-demo", claim.getMetadata().getName());

            // check volume mount
            Volume volume = pod.getSpec().getVolumes().get(0);
            Assertions.assertEquals("home-dir-pvc-demo", volume.getName());
            Assertions.assertEquals("home-dir-pvc-demo", volume.getPersistentVolumeClaim().getClaimName());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);

            // all additional manifests should be deleted
            assertNoPvcs(k8s);
        }
    }

    /**
     * Tests the creation and deleting of additional manifests (both persistent and non-persistent).
     * The first manifest contains a namespace definition and is non-persistent (it is a secret).
     * The second manifest does not contain a namespace definition, but in the end should have the same namespace as the pod.
     * It is a PVC which should be persistent.
     */
    @Test
    public void launchProxyWithAdditionalPersistentManifests() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_manifests_persistent");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.overriddenNamespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.overriddenNamespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            PersistentVolumeClaimList claimList = k8s.client.persistentVolumeClaims().inNamespace(k8s.overriddenNamespace).list();
            Assertions.assertEquals(1, claimList.getItems().size());
            PersistentVolumeClaim claim = claimList.getItems().get(0);
            Assertions.assertEquals(k8s.overriddenNamespace, claim.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-pvc", claim.getMetadata().getName());

            // secret has no namespace defined -> should be created in the namespace defined by the pod patches
            Secret secret = k8s.getSingleSecret(k8s.overriddenNamespace);
            Assertions.assertEquals(k8s.overriddenNamespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should be deleted
            assertNoSecrets(k8s);

            // the PVC should not be deleted
            Assertions.assertEquals(1, k8s.client.persistentVolumeClaims().inNamespace(k8s.overriddenNamespace).list().getItems().size());
        }
    }

    @Test
    public void launchProxyWithManifestPolicyCreateOnce() {
        // case 1: secret does not exist yet
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_manifests_policy_create_once");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            // secret should have the value from the application.yml
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should be deleted
            assertNoSecrets(k8s);
        }
        // case 2: secret does already exist, check that it does not get overridden
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // first create secret
            k8s.client.secrets().inNamespace(k8s.namespace).create(new SecretBuilder()
                .withNewMetadata()
                .withName("manifests-secret")
                .withLabels(Map.of(
                    "openanalytics.eu/sp-additional-manifest", "true",
                    "openanalytics.eu/sp-persistent-manifest", "false",
                    "openanalytics.eu/sp-manifest-id", "994e40ac66b4bc9329b97d776146b66679afb2b9"
                ))
                .endMetadata()
                .withData(Collections.singletonMap("password", "b2xkX3Bhc3N3b3Jk"))
                .build());

            String id = inst.client.startProxy("01_hello_manifests_policy_create_once");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            // secret should have the value from the secret we created above not from the application.yml
            Assertions.assertEquals("b2xkX3Bhc3N3b3Jk", secret.getData().get("password"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should be deleted
            assertNoSecrets(k8s);
        }
    }

    @Test
    public void launchProxyWithManifestPolicyPatch() {
        // case 1: secret does not exist yet
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_manifests_policy_patch");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            // secret should have the value from the application.yml
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should be deleted
            assertNoSecrets(k8s);
        }

        // case 2: secret does already exist, check that it gets overridden
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // first create secret
            Secret originalSecret = k8s.client.secrets().inNamespace(k8s.namespace).create(new SecretBuilder()
                .withNewMetadata()
                .withName("manifests-secret")
                .endMetadata()
                .withData(Collections.singletonMap("password", "b2xkX3Bhc3N3b3Jk"))
                .build());

            String originalCreationTimestamp = originalSecret.getMetadata().getCreationTimestamp();

            // let the secret live for some seconds, so that the timestamp will be different
            TestUtil.sleep(2000);
            String id = inst.client.startProxy("01_hello_manifests_policy_patch");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            // secret should have the value from the application.yml not from the secret we created above
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));
            // since the secret should not have been replaced, the creation timestamp must be equal
            Assertions.assertEquals(originalCreationTimestamp, secret.getMetadata().getCreationTimestamp());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should be deleted
            assertNoSecrets(k8s);
        }
    }

    @Test
    public void launchProxyWithManifestPolicyDelete() {
        // case 1: secret does already exist, check that it gets deleted
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // first create secret
            k8s.client.secrets().inNamespace(k8s.namespace).create(new SecretBuilder()
                .withNewMetadata()
                .withName("manifests-secret")
                .endMetadata()
                .withData(Collections.singletonMap("password", "b2xkX3Bhc3N3b3Jk"))
                .build());

            String id = inst.client.startProxy("01_hello_manifests_policy_delete");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // the secret should be deleted
            assertNoSecrets(k8s);

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithManifestPolicyReplace() {
        // case 1: secret does not exist yet
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_manifests_policy_replace");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            // secret should have the value from the application.yml
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should be deleted
            assertNoSecrets(k8s);
        }

        // case 2: secret does already exist, check that it gets overridden
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // first create secret
            Secret originalSecret = k8s.client.secrets().inNamespace(k8s.namespace).create(new SecretBuilder()
                .withNewMetadata()
                .withName("manifests-secret")
                .endMetadata()
                .withData(Collections.singletonMap("password", "b2xkX3Bhc3N3b3Jk"))
                .build());

            String originalCreationTimestamp = originalSecret.getMetadata().getCreationTimestamp();

            // let the secret live for some seconds, so that the timestamp will be different
            TestUtil.sleep(2000);

            String id = inst.client.startProxy("01_hello_manifests_policy_replace");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            // secret should have the value from the application.yml not from the secret we created above
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));
            // since the secret was replaced, the creation timestamp must be different
            Assertions.assertNotEquals(originalCreationTimestamp, secret.getMetadata().getCreationTimestamp());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should be deleted
            assertNoSecrets(k8s);
        }
    }

    @Test
    public void launchProxyWithPersistentManifestPolicyCreateOnce() {
        // case 1: secret does not exist yet
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_persistent_manifests_policy_create_once");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);

            // the secret should not be deleted
            Assertions.assertEquals(1, k8s.getSecrets(k8s.namespace).size());

            String originalCreationTimestamp = k8s.client.secrets().inNamespace(k8s.namespace)
                .withName("manifests-secret").get().getMetadata().getCreationTimestamp();

            // step 2: secret does already exist, check that it gets overridden
            id = inst.client.startProxy("01_hello_persistent_manifests_policy_create_once");
            proxy = inst.proxyService.getProxy(id);

            // secret has no namespace defined -> should be created in the default namespace
            secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));
            // secret should have not been modified -> check timestamp
            Assertions.assertEquals(originalCreationTimestamp, secret.getMetadata().getCreationTimestamp());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should not be deleted
            Assertions.assertEquals(1, k8s.getSecrets(k8s.namespace).size());
        }
    }

    @Test
    public void launchProxyWithPersistentManifestPolicyPatch() {
        // case 1: secret does not exist yet
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_persistent_manifests_policy_patch");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);

            // the secret should not be deleted
            Assertions.assertEquals(1, k8s.getSecrets(k8s.namespace).size());

            String originalCreationTimestamp = k8s.client.secrets().inNamespace(k8s.namespace)
                .withName("manifests-secret").get().getMetadata().getCreationTimestamp();

            // same spec, different value
            id = inst.client.startProxy("01_hello_persistent_manifests_policy_patch2");
            proxy = inst.proxyService.getProxy(id);

            // secret has no namespace defined -> should be created in the default namespace
            secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            // secret should have the value from the second spec
            Assertions.assertEquals("b2xkX3Bhc3N3b3Jk", secret.getData().get("password"));
            // since the secret should not have been replaced, the creation timestamp must be equal
            Assertions.assertEquals(originalCreationTimestamp, secret.getMetadata().getCreationTimestamp());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should not be deleted
            Assertions.assertEquals(1, k8s.getSecrets(k8s.namespace).size());
        }
    }

    @Test
    public void launchProxyWithPersistentManifestPolicyDelete() {
        // case 1: secret does already exist, check that it gets deleted
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // first create secret
            k8s.client.secrets().inNamespace(k8s.namespace).create(new SecretBuilder()
                .withNewMetadata()
                .withName("manifests-secret")
                .endMetadata()
                .withData(Collections.singletonMap("password", "b2xkX3Bhc3N3b3Jk"))
                .build());

            String id = inst.client.startProxy("01_hello_persistent_manifests_policy_delete");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // the secret should be deleted
            assertNoSecrets(k8s);

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithPersistentManifestPolicyReplace() {
        // case 1: secret does not exist yet
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_persistent_manifests_policy_replace");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            // secret has no namespace defined -> should be created in the default namespace
            Secret secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            Assertions.assertEquals("cGFzc3dvcmQ=", secret.getData().get("password"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);

            // the secret should not be deleted
            Assertions.assertEquals(1, k8s.getSecrets(k8s.namespace).size());

            String originalCreationTimestamp = k8s.client.secrets().inNamespace(k8s.namespace)
                .withName("manifests-secret").get().getMetadata().getCreationTimestamp();

            // same spec, different value
            id = inst.client.startProxy("01_hello_persistent_manifests_policy_replace2");
            proxy = inst.proxyService.getProxy(id);

            // secret has no namespace defined -> should be created in the default namespace
            secret = k8s.getSingleSecret(k8s.namespace);
            Assertions.assertEquals(k8s.namespace, secret.getMetadata().getNamespace());
            Assertions.assertEquals("manifests-secret", secret.getMetadata().getName());
            Assertions.assertEquals("b2xkX3Bhc3N3b3Jk", secret.getData().get("password"));
            // since the secret should have been replaced, the creation timestamp must be different
            Assertions.assertNotEquals(originalCreationTimestamp, secret.getMetadata().getCreationTimestamp());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
            // the secret should not be deleted
            Assertions.assertEquals(1, k8s.getSecrets(k8s.namespace).size());
        }
    }


    /**
     * This test starts a Proxy with an advanced setup of RuntimeLabels and environment variables + labels.
     */
    @Test
    public void advancedRuntimeLabels() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_advanced_runtime_labels");
            Proxy proxy = inst.proxyService.getProxy(id);
            String containerId = proxy.getContainers().get(0).getId();

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.namespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));
            Assertions.assertFalse(pod.getSpec().getContainers().get(0).getSecurityContext().getPrivileged());

            ServiceList serviceList = k8s.client.services().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, serviceList.getItems().size());
            Service service = serviceList.getItems().get(0);
            Assertions.assertEquals(k8s.namespace, service.getMetadata().getNamespace());
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


            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);

            // all services should be deleted
            assertNoServices(k8s);
        }
    }

    @Test
    public void launchProxyWithParameters() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("parameters", Map.of(
                "environment", "base_r",
                "version", "4.0.5",
                "memory", "2G"
            ));
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);

            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("ledfan/rstudio_base_r:4_0_5"));
            Assertions.assertEquals("2", pod.getSpec().getContainers().get(0).getResources().getLimits().get("memory").getAmount());
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));

            Assertions.assertTrue(env.containsKey("ENVIRONMENT"));
            Assertions.assertEquals("ledfan/rstudio_base_r", env.get("ENVIRONMENT").getValue());
            Assertions.assertTrue(env.containsKey("VERSION"));
            Assertions.assertEquals("4_0_5", env.get("VERSION").getValue());
            Assertions.assertTrue(env.containsKey("MEMORY"));
            Assertions.assertEquals("2G", env.get("MEMORY").getValue());
            Assertions.assertTrue(env.containsKey("VALUESET_NAME"));
            Assertions.assertEquals("the-first-value-set", env.get("VALUESET_NAME").getValue());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithParametersWithNullValueSetName() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("parameters-null", Map.of(
                "environment", "base_r",
                "version", "4.0.5",
                "memory", "2G"
            ));
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);

            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("ledfan/rstudio_base_r:4_0_5"));
            Assertions.assertEquals("2", pod.getSpec().getContainers().get(0).getResources().getLimits().get("memory").getAmount());
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));

            Assertions.assertTrue(env.containsKey("VALUESET_NAME"));
            Assertions.assertNull(env.get("VALUESET_NAME").getValue());

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithParametersWithError() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // case 1: test using API
            JsonObject resp = inst.client.startProxyError("parameters-error", Map.of(
                "environment", "base_r",
                "version", "4.0.5",
                "memory", "2G"
            ));
            Assertions.assertEquals(resp.getString("status"), "error");
            Assertions.assertEquals(resp.getString("data"), "Failed to start proxy");

            // case 2: test using ProxyService
            Authentication auth = UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of(new SimpleGrantedAuthority("ROLE_GROUP1")));
            ProxySpec spec = inst.specProvider.getSpec("parameters-error");
            ContainerProxyException ex = Assertions.assertThrows(ContainerProxyException.class, () -> {
                inst.proxyService.startProxy(auth, spec, null,
                        UUID.randomUUID().toString(),
                        Map.of(
                            "environment", "base_r",
                            "version", "4.0.5",
                            "memory", "2G"
                        ))
                    .run();
            }, "The parameter with id \"non-existing-parameter\" does not exist");

            Assertions.assertEquals("Container failed to start", ex.getMessage());
            Assertions.assertEquals(ProxyFailedToStartException.class, ex.getCause().getClass());
            Assertions.assertEquals(SpelException.class, ex.getCause().getCause().getClass());
            Assertions.assertEquals("""
                Error while resolving expression: "- op: add
                  path: /spec/containers/0/env/-
                  value:
                    name: ERROR
                    value: "#{proxy.getRuntimeObject('SHINYPROXY_PARAMETERS').getValue('non-existing-parameter')}"
                ", error: The parameter with id "non-existing-parameter" does not exist!""", ex.getCause().getCause().getMessage());

            // no pods created
            assertNoPods(k8s);
        }
    }

    @Test
    public void launchProxyWithParametersFinalResolve() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("parameters-final-resolve", Map.of(
                "environment", "base_r",
                "version", "4.0.5",
                "memory", "2G"
            ));
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.namespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);

            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("ledfan/rstudio_base_r:4_0_5"));
            Assertions.assertEquals("2", pod.getSpec().getContainers().get(0).getResources().getLimits().get("memory").getAmount());
            List<EnvVar> envList = pod.getSpec().getContainers().get(0).getEnv();
            Map<String, EnvVar> env = envList.stream().collect(Collectors.toMap(EnvVar::getName, e -> e));

            Assertions.assertTrue(env.containsKey("ENVIRONMENT"));
            Assertions.assertEquals("ledfan/rstudio_base_r", env.get("ENVIRONMENT").getValue());
            Assertions.assertTrue(env.containsKey("VERSION"));
            Assertions.assertEquals("4_0_5", env.get("VERSION").getValue());
            Assertions.assertTrue(env.containsKey("MEMORY"));
            Assertions.assertEquals("2G", env.get("MEMORY").getValue());
            Assertions.assertTrue(env.containsKey("VALUESET_NAME"));
            Assertions.assertEquals("the-first-value-set", env.get("VALUESET_NAME").getValue());

            // env vars that should be resolved during the final resolve:
            Assertions.assertTrue(env.containsKey("HEARTBEAT_TIMEOUT"));
            Assertions.assertEquals("160000", env.get("HEARTBEAT_TIMEOUT").getValue());
            Assertions.assertTrue(env.containsKey("MAX_LIFETIME"));
            Assertions.assertEquals("-1", env.get("MAX_LIFETIME").getValue());
            Assertions.assertTrue(env.containsKey("MEMORY_LIMIT"));
            Assertions.assertEquals("2G", env.get("MEMORY_LIMIT").getValue());
            Assertions.assertTrue(env.containsKey("IMAGE"));
            Assertions.assertEquals("ledfan/rstudio_base_r:4_0_5", env.get("IMAGE").getValue());

            // labels that should be resolved during the final resolve:
            Map<String, String> labels = pod.getMetadata().getLabels();
            Assertions.assertTrue(labels.containsKey("HEARTBEAT_TIMEOUT"));
            Assertions.assertEquals("160000", labels.get("HEARTBEAT_TIMEOUT"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);
        }
    }


    /**
     * Tests the creation and deleting of additional manifests (both persistent and non-persistent) using auth objects inside.
     * See #26789
     */
    @Test
    public void launchProxyWithAdditionalPersistentManifestsUsingAuthObjects() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id = inst.client.startProxy("01_hello_manifests_persistent_using_auth");
            Proxy proxy = inst.proxyService.getProxy(id);

            PodList podList = k8s.client.pods().inNamespace(k8s.overriddenNamespace).list();
            Assertions.assertEquals(1, podList.getItems().size());
            Pod pod = podList.getItems().get(0);
            Assertions.assertEquals("Running", pod.getStatus().getPhase());
            Assertions.assertEquals(k8s.overriddenNamespace, pod.getMetadata().getNamespace());
            Assertions.assertEquals("sp-pod-" + proxy.getId() + "-0", pod.getMetadata().getName());
            Assertions.assertEquals(1, pod.getStatus().getContainerStatuses().size());
            ContainerStatus container = pod.getStatus().getContainerStatuses().get(0);
            Assertions.assertEquals(true, container.getReady());
            Assertions.assertTrue(container.getImage().endsWith("openanalytics/shinyproxy-integration-test-app:latest"));

            ConfigMap configMap1 = k8s.client.configMaps().inNamespace(k8s.overriddenNamespace).withName("configmap1").get();
            Assertions.assertTrue(configMap1.getData().containsKey("test.txt"));
            Assertions.assertEquals("GROUP1,GROUP2\n", configMap1.getData().get("test.txt"));

            ConfigMap configMap2 = k8s.client.configMaps().inNamespace(k8s.overriddenNamespace).withName("configmap2").get();
            Assertions.assertTrue(configMap2.getData().containsKey("test.txt"));
            Assertions.assertEquals("GROUP1,GROUP2\n", configMap2.getData().get("test.txt"));

            inst.proxyService.stopProxy(null, proxy, true).run();

            // all pods should be deleted
            assertNoPods(k8s);

            // configmap 1 should have been deleted
            configMap1 = k8s.client.configMaps().inNamespace(k8s.overriddenNamespace).withName("configmap1").get();
            Assertions.assertNull(configMap1);
            // configmap 2 should not have been deleted
            configMap2 = k8s.client.configMaps().inNamespace(k8s.overriddenNamespace).withName("configmap2").get();
            Assertions.assertTrue(configMap2.getData().containsKey("test.txt"));
        }
    }

    private void assertNoPods(ContainerSetup k8s) {
        boolean cleanedUp = Retrying.retry((c, m) -> {
            List<Pod> pods1 = k8s.client.pods().inNamespace(k8s.namespace).list().getItems();
            List<Pod> pods2 = k8s.client.pods().inNamespace(k8s.overriddenNamespace).list().getItems();
            return pods1.isEmpty() && pods2.isEmpty();
        }, 5_000, "assert no pods", 1, true);
        Assertions.assertTrue(cleanedUp);
        Assertions.assertEquals(0, inst.proxyService.getAllProxies().size());
    }

    private void assertNoPvcs(ContainerSetup k8s) {
        boolean cleanedUp = Retrying.retry((c, m) -> {
            List<PersistentVolumeClaim> pods1 = k8s.client.persistentVolumeClaims().inNamespace(k8s.namespace).list().getItems();
            List<PersistentVolumeClaim> pods2 = k8s.client.persistentVolumeClaims().inNamespace(k8s.overriddenNamespace).list().getItems();
            return pods1.isEmpty() && pods2.isEmpty();
        }, 5_000, "assert no pvcs", 1, true);
        Assertions.assertTrue(cleanedUp);
    }

    private void assertNoSecrets(ContainerSetup k8s) {
        boolean cleanedUp = Retrying.retry((c, m) -> {
            List<Secret> pods1 = k8s.getSecrets(k8s.namespace);
            List<Secret> pods2 = k8s.getSecrets(k8s.overriddenNamespace);
            return pods1.isEmpty() && pods2.isEmpty();
        }, 5_000, "assert no secrets", 1, true);
        Assertions.assertTrue(cleanedUp);
    }

    private void assertNoServices(ContainerSetup k8s) {
        boolean cleanedUp = Retrying.retry((c, m) -> {
            List<Service> pods1 = k8s.client.services().inNamespace(k8s.namespace).list().getItems();
            List<Service> pods2 = k8s.client.services().inNamespace(k8s.overriddenNamespace).list().getItems();
            return pods1.isEmpty() && pods2.isEmpty();
        }, 5_000, "assert no secrets", 1, true);
        Assertions.assertTrue(cleanedUp);
    }

}
