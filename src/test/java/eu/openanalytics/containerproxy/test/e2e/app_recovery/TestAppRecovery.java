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
package eu.openanalytics.containerproxy.test.e2e.app_recovery;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import eu.openanalytics.containerproxy.test.helpers.KubernetesTestBase;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyClient;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.json.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

public class TestAppRecovery extends KubernetesTestBase {

    private static Stream<Arguments> provideStringsForIsBlank() {
        return Stream.of(
                Arguments.of("docker", ""),
                Arguments.of("docker", "--proxy.docker.internal-networking=true"),
                Arguments.of("docker-swarm", ""),
                Arguments.of("docker-swarm", "--proxy.docker.internal-networking=true"),
                Arguments.of("kubernetes", ""),
                Arguments.of("kubernetes", "--proxy.docker.internal-networking=true")
        );
    }

    private void assertEverythingCleanedUp() throws DockerCertificateException, DockerException, InterruptedException {
        // Docker
        DefaultDockerClient dockerClient = DefaultDockerClient.fromEnv().build();
        Assertions.assertEquals(0, dockerClient.listContainers().stream()
                .filter(it -> it.labels() != null && it.labels().containsKey("openanalytics.eu/sp-proxied-app"))
                .count());

        // Docker swarm
        Assertions.assertEquals(0, dockerClient.listServices().size());

        // k8s
        List<Pod> pods = client.pods().inNamespace(namespace).list().getItems();
        Assertions.assertEquals(0, pods.size());
        pods = client.pods().inNamespace(overriddenNamespace).list().getItems();
        Assertions.assertEquals(0, pods.size());
        pods = client.pods().inNamespace("default").list().getItems();
        Assertions.assertEquals(0, pods.size());
    }

    @AfterEach
    public void waitForCleanup() throws InterruptedException, DockerException, DockerCertificateException {
        Thread.sleep(20_000);
        assertEverythingCleanedUp();
    }

    @BeforeEach
    public void beforeEach() throws DockerCertificateException, DockerException, InterruptedException {
        assertEverythingCleanedUp();
    }

    @ParameterizedTest
    @MethodSource("provideStringsForIsBlank")
    public void simple_recover_single_app_after_shutdown(String backend, String extraArgs) throws IOException, InterruptedException {
        ShinyProxyClient shinyProxyClient = new ShinyProxyClient("demo", "demo");
        List<ShinyProxyInstance> instances = new ArrayList<>();
        try {
            // 1. create the instance
            ShinyProxyInstance instance1 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), extraArgs);
            instances.add(instance1);
            Assertions.assertTrue(instance1.start());

            // 2. create a proxy
            String id = shinyProxyClient.startProxy("01_hello");
            Assertions.assertNotNull(id);

            // 3. get defined proxies
            HashSet<JsonObject> originalProxies = shinyProxyClient.getProxies();
            Assertions.assertNotNull(originalProxies);
            Assertions.assertEquals(1, originalProxies.size());

            // 4. stop the instance
            instance1.stop();

            // 5. start the instance again
            ShinyProxyInstance instance2 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), extraArgs);
            instances.add(instance2);
            Assertions.assertTrue(instance2.start());

            // 6. get defined proxies
            HashSet<JsonObject> newProxies = shinyProxyClient.getProxies();
            Assertions.assertNotNull(newProxies);

            // 7. assert that the responses are equal
            Assertions.assertEquals(originalProxies, newProxies);

            // 8. stop the proxy
            Assertions.assertTrue(shinyProxyClient.stopProxy(id));

            // 9. stop the instance
            instance2.stop();
        } finally {
            instances.forEach(ShinyProxyInstance::stop);
        }
    }

    private static Stream<Arguments> new_app_should_work_after_recovery_src() {
        return Stream.of(
                Arguments.of("docker", ""),
                Arguments.of("docker-swarm", ""),
                Arguments.of("kubernetes", ""),
                Arguments.of("kubernetes", "--proxy.docker.internal-networking=true")
        );
    }

    // note: this test only works with minikube running on the same local machine, because it uses the NodePort services
    @ParameterizedTest
    @MethodSource("new_app_should_work_after_recovery_src")
    public void new_app_should_work_after_recovery(String backend, String extraArgs) throws IOException, InterruptedException {
        ShinyProxyClient shinyProxyClient = new ShinyProxyClient("demo", "demo");
        List<ShinyProxyInstance> instances = new ArrayList<>();
        try {
            // 1. create the instance
            ShinyProxyInstance instance1 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), extraArgs);
            instances.add(instance1);
            Assertions.assertTrue(instance1.start());

            // 2. create a proxy
            String id1 = shinyProxyClient.startProxy("01_hello");
            Assertions.assertNotNull(id1);
            Thread.sleep(10000); // give the app some time to get ready (we are not using ShinyProxyTestStrategy, so status is not reliable)
            Assertions.assertTrue(shinyProxyClient.getProxyRequest(id1));


            // 3. get defined proxies
            HashSet<JsonObject> originalProxies = shinyProxyClient.getProxies();
            Assertions.assertNotNull(originalProxies);

            // 4. stop the instance
            instance1.stop();

            // 5. start the instance again
            ShinyProxyInstance instance2 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), extraArgs);
            instances.add(instance2);
            Assertions.assertTrue(instance2.start());

            // 6. get defined proxies
            HashSet<JsonObject> newProxies = shinyProxyClient.getProxies();
            Assertions.assertNotNull(newProxies);

            // 7. assert that the responses are equal
            Assertions.assertEquals(originalProxies, newProxies);

            // 8. create a proxy
            String id2 = shinyProxyClient.startProxy("02_hello");
            Assertions.assertNotNull(id2);
            Thread.sleep(10000); // give the app some time to get ready (we are not using ShinyProxyTestStrategy, so status is not reliable)

            // 9. test if both proxies are still reachable
            Assertions.assertTrue(shinyProxyClient.getProxyRequest(id1));
            Assertions.assertTrue(shinyProxyClient.getProxyRequest(id2));

            // 8. stop both proxy
            Assertions.assertTrue(shinyProxyClient.stopProxy(id1));
            Assertions.assertTrue(shinyProxyClient.stopProxy(id2));

            // 9. stop the instance
            instance2.stop();
        } finally {
            instances.forEach(ShinyProxyInstance::stop);
        }
    }

    @ParameterizedTest
    @MethodSource("provideStringsForIsBlank")
    public void complex_recover_multiple_apps_after_shutdown(String backend, String extraArgs) throws IOException, InterruptedException {
        ShinyProxyClient shinyProxyClient1 = new ShinyProxyClient("demo", "demo");
        ShinyProxyClient shinyProxyClient2 = new ShinyProxyClient("demo2", "demo2");
        ShinyProxyClient shinyProxyClient3 = new ShinyProxyClient("demo3", "demo3");

        List<ShinyProxyInstance> instances = new ArrayList<>();
        try {
            // 1. create the instance
            ShinyProxyInstance instance1 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), extraArgs);
            instances.add(instance1);
            Assertions.assertTrue(instance1.start());


            // 2. create two proxies for user demo
            String id1 = shinyProxyClient1.startProxy("01_hello");
            Assertions.assertNotNull(id1);

            String id2 = shinyProxyClient1.startProxy("02_hello");
            Assertions.assertNotNull(id2);

            // 3. get defined proxies
            HashSet<JsonObject> originalProxies1 = shinyProxyClient1.getProxies();
            Assertions.assertNotNull(originalProxies1);
            Assertions.assertEquals(2, originalProxies1.size());


            // 4. create two proxies for user demo
            String id3 = shinyProxyClient2.startProxy("01_hello");
            Assertions.assertNotNull(id3);

            String id4 = shinyProxyClient2.startProxy("02_hello");
            Assertions.assertNotNull(id4);

            // 5. get defined proxies
            HashSet<JsonObject> originalProxies2 = shinyProxyClient2.getProxies();
            Assertions.assertNotNull(originalProxies2);


            // 6. create two proxies for user demo
            String id5 = shinyProxyClient3.startProxy("01_hello");
            Assertions.assertNotNull(id5);

            String id6 = shinyProxyClient3.startProxy("02_hello");
            Assertions.assertNotNull(id6);

            // 7. get defined proxies
            HashSet<JsonObject> originalProxies3 = shinyProxyClient3.getProxies();
            Assertions.assertNotNull(originalProxies3);


            // 8. stop the instance
            instance1.stop();

            // 9. start the instance again
            ShinyProxyInstance instance2 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), extraArgs);
            instances.add(instance2);
            Assertions.assertTrue(instance2.start());

            // 10. get defined proxies for user demo
            HashSet<JsonObject> newProxies1 = shinyProxyClient1.getProxies();
            Assertions.assertNotNull(newProxies1);

            // 11. assert that the responses are equal
            Assertions.assertEquals(originalProxies1, newProxies1);


            // 12. get defined proxies for user demo2
            HashSet<JsonObject> newProxies2 = shinyProxyClient2.getProxies();
            Assertions.assertNotNull(newProxies2);

            // 13. assert that the responses are equal
            Assertions.assertEquals(originalProxies2, newProxies2);


            // 14. get defined proxies for user demo3
            HashSet<JsonObject> newProxies3 = shinyProxyClient3.getProxies();
            Assertions.assertNotNull(newProxies3);

            // 15. assert that the responses are equal
            Assertions.assertEquals(originalProxies3, newProxies3);


            // 16. stop the proxies
            Assertions.assertTrue(shinyProxyClient1.stopProxy(id1));
            Assertions.assertTrue(shinyProxyClient1.stopProxy(id2));
            Assertions.assertTrue(shinyProxyClient2.stopProxy(id3));
            Assertions.assertTrue(shinyProxyClient2.stopProxy(id4));
            Assertions.assertTrue(shinyProxyClient3.stopProxy(id5));
            Assertions.assertTrue(shinyProxyClient3.stopProxy(id6));

            // 17. stop the instance
            instance2.stop();
        } finally {
            instances.forEach(ShinyProxyInstance::stop);
        }
    }

    @ParameterizedTest
    @MethodSource("provideStringsForIsBlank")
    public void simple_recover_multiple_instances(String backend, String extraArgs) throws IOException, InterruptedException {
        ShinyProxyClient shinyProxyClient1 = new ShinyProxyClient("demo", "demo", 7583);
        ShinyProxyClient shinyProxyClient2 = new ShinyProxyClient("demo", "demo", 7584);
        List<ShinyProxyInstance> instances = new ArrayList<>();
        try {
            // 1. create the first instance
            ShinyProxyInstance instance1 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), 7583, extraArgs);
            instances.add(instance1);
            Assertions.assertTrue(instance1.start());

            // 1. create the second instance
            ShinyProxyInstance instance2 = new ShinyProxyInstance(String.format("application-app-recovery_%s_2.yml", backend), 7584, extraArgs);
            instances.add(instance2);
            Assertions.assertTrue(instance2.start());

            // 2. create a proxy on both instances
            String id1 = shinyProxyClient1.startProxy("01_hello");
            Assertions.assertNotNull(id1);

            String id2 = shinyProxyClient2.startProxy("01_hello");
            Assertions.assertNotNull(id2);

            // 3. get defined proxies
            HashSet<JsonObject> originalProxies1 = shinyProxyClient1.getProxies();
            Assertions.assertNotNull(originalProxies1);
            Assertions.assertEquals(1, originalProxies1.size());

            HashSet<JsonObject> originalProxies2 = shinyProxyClient2.getProxies();
            Assertions.assertNotNull(originalProxies2);
            Assertions.assertEquals(1, originalProxies2.size());

            // 4. stop both instances
            instance1.stop();
            instance2.stop();

            // 5. start both instances again
            ShinyProxyInstance instance3 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), 7583, extraArgs);
            instances.add(instance3);
            Assertions.assertTrue(instance3.start());

            ShinyProxyInstance instance4 = new ShinyProxyInstance(String.format("application-app-recovery_%s_2.yml", backend), 7584, extraArgs);
            instances.add(instance4);
            Assertions.assertTrue(instance4.start());

            // 6. get defined proxies
            HashSet<JsonObject> newProxies1 = shinyProxyClient1.getProxies();
            Assertions.assertNotNull(newProxies1);

            HashSet<JsonObject> newProxies2 = shinyProxyClient2.getProxies();
            Assertions.assertNotNull(newProxies2);

            // 7. assert that the responses are equal
            Assertions.assertEquals(originalProxies1, newProxies1);
            Assertions.assertEquals(originalProxies2, newProxies2);

            // 8. stop the proxy
            Assertions.assertTrue(shinyProxyClient1.stopProxy(id1));
            Assertions.assertTrue(shinyProxyClient2.stopProxy(id2));

            // 9. stop the instance
            instance3.stop();
            instance4.stop();
        } finally {
            instances.forEach(ShinyProxyInstance::stop);
        }
    }

    @Test
    public void kubernetes_multiple_namespaces() {
        setup((client, namespace, overriddenNamespace) -> {
            ShinyProxyClient shinyProxyClient = new ShinyProxyClient("demo", "demo");
            List<ShinyProxyInstance> instances = new ArrayList<>();
            try {
                // 1. create the instance
                ShinyProxyInstance instance1 = new ShinyProxyInstance("application-app-recovery_kubernetes_multi_ns.yml");
                instances.add(instance1);
                Assertions.assertTrue(instance1.start());

                // 2. create a proxy
                String id1 = shinyProxyClient.startProxy("01_hello");
                Assertions.assertNotNull(id1);

                String id2 = shinyProxyClient.startProxy("02_hello");
                Assertions.assertNotNull(id2);

                // 3. get defined proxies
                HashSet<JsonObject> originalProxies = shinyProxyClient.getProxies();
                Assertions.assertNotNull(originalProxies);

                // 4. stop the instance
                instance1.stop();

                // 5. start the instance again
                ShinyProxyInstance instance2 = new ShinyProxyInstance("application-app-recovery_kubernetes_multi_ns.yml");
                instances.add(instance2);
                Assertions.assertTrue(instance2.start());

                // 6. get defined proxies
                HashSet<JsonObject> newProxies = shinyProxyClient.getProxies();
                Assertions.assertNotNull(newProxies);

                // 7. assert that the responses are equal
                Assertions.assertEquals(originalProxies, newProxies);

                // 8. stop the proxy
                Assertions.assertTrue(shinyProxyClient.stopProxy(id1));
                Assertions.assertTrue(shinyProxyClient.stopProxy(id2));

                // 9. stop the instance
                instance2.stop();
            } finally {
                instances.forEach(ShinyProxyInstance::stop);
            }
        });

    }

    @Test
    public void shutdown_should_cleanup_by_default() {
        setup((client, namespace, overriddenNamespace) -> {
            ShinyProxyClient shinyProxyClient = new ShinyProxyClient("demo", "demo");
            List<ShinyProxyInstance> instances = new ArrayList<>();
            try {
                // 1. create the instance
                ShinyProxyInstance instance1 = new ShinyProxyInstance("application-app-recovery_kubernetes_normal_shutdown.yml");
                instances.add(instance1);
                Assertions.assertTrue(instance1.start());

                // 2. create a proxy
                String id = shinyProxyClient.startProxy("01_hello");
                Assertions.assertNotNull(id);

                // 3. stop the instance
                instance1.stop();

                // 4. wait for cleanup
                Thread.sleep(5000);

                // 4. check if pod is cleanup correctly
                List<Pod> pods = client.pods().inNamespace(overriddenNamespace).list().getItems();
                Assertions.assertEquals(0, pods.size());

            } finally {
                instances.forEach(ShinyProxyInstance::stop);
            }
        });
    }

}
