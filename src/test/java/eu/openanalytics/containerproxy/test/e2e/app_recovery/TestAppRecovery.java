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

import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyClient;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import eu.openanalytics.containerproxy.test.helpers.TestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.json.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;


public class TestAppRecovery {

    private static Stream<Arguments> backends() {
        return Stream.of(Arguments.of("docker", new HashMap<String, String>()), Arguments.of("docker", new HashMap<String, String>() {{
                put("proxy.docker.internal-networking", "true");
            }}), Arguments.of("docker-swarm", new HashMap<String, String>()), Arguments.of("docker-swarm", new HashMap<String, String>() {{
                put("proxy.docker.internal-networking", "true");
            }}), Arguments.of("kubernetes", new HashMap<String, String>() {{
                put("proxy.kubernetes.namespace", "itest");
            }}), Arguments.of("kubernetes", new HashMap<String, String>() {{
                put("proxy.kubernetes.namespace", "itest");
                put("proxy.kubernetes.internal-networking", "true");
            }})
        );
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void simple_recover_single_app_after_shutdown(String backend, Map<String, String> properties) {
        try (ContainerSetup k8s = new ContainerSetup(backend)) {
            HashSet<JsonObject> originalProxies;
            String id;

            // 1. create the instance
            try (ShinyProxyInstance inst = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), properties)) {
                // 2. create a proxy
                id = inst.client.startProxy("01_hello");
                // 3. get defined proxies
                originalProxies = inst.client.getProxies();
                Assertions.assertNotNull(originalProxies);
                Assertions.assertEquals(1, originalProxies.size());
            }

            // 4. start the instance again
            try (ShinyProxyInstance inst = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), properties)) {
                // 5. get defined proxies
                HashSet<JsonObject> newProxies = inst.client.getProxies();
                // 6. assert that the responses are equal
                Assertions.assertNotNull(newProxies);
                Assertions.assertEquals(originalProxies, newProxies);
                // 7. stop the proxy
                inst.client.stopProxy(id);
            }
        }
    }

    private static Stream<Arguments> new_app_should_work_after_recovery_src() {
        return Stream.of(Arguments.of("docker", new HashMap<String, String>()), Arguments.of("docker-swarm", new HashMap<String, String>()), Arguments.of("kubernetes", new HashMap<String, String>()));
    }

    // note: this test only works with minikube running on the same local machine, because it uses the NodePort services
    @ParameterizedTest
    @MethodSource("new_app_should_work_after_recovery_src")
    public void new_app_should_work_after_recovery(String backend, Map<String, String> properties) {
        try (ContainerSetup k8s = new ContainerSetup(backend)) {
            HashSet<JsonObject> originalProxies;
            String id1;
            // 1. create the instance
            try (ShinyProxyInstance inst = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), properties, true)) {
                // 2. create a proxy
                id1 = inst.client.startProxy("01_hello");
                Assertions.assertNotNull(id1);
                inst.client.testProxyReachable(id1);
                // 3. get defined proxies
                originalProxies = inst.client.getProxies();
                Assertions.assertNotNull(originalProxies);
                Assertions.assertEquals(1, originalProxies.size());
            }

            // 5. start the instance again
            try (ShinyProxyInstance inst = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), properties, true)) {
                // 6. get defined proxies
                HashSet<JsonObject> newProxies = inst.client.getProxies();
                Assertions.assertNotNull(newProxies);

                // 7. assert that the responses are equal
                Assertions.assertEquals(originalProxies, newProxies);

                // 8. create a proxy
                String id2 = inst.client.startProxy("02_hello");
                Assertions.assertNotNull(id2);

                // 9. test if both proxies are still
                inst.client.testProxyReachable(id1);
                inst.client.testProxyReachable(id2);

                // 10. stop both proxies
                inst.client.stopProxy(id1);
                inst.client.stopProxy(id2);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void complex_recover_multiple_apps_after_shutdown(String backend, Map<String, String> properties) {
        try (ContainerSetup k8s = new ContainerSetup(backend)) {
            String id1, id2, id3, id4, id5, id6;
            HashSet<JsonObject> originalProxies1, originalProxies2, originalProxies3;

            // 1. create the instance
            try (ShinyProxyInstance inst = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), properties)) {
                ShinyProxyClient clientDemo2 = inst.getClient("demo2");
                ShinyProxyClient clientDemo3 = inst.getClient("demo3");

                // 2. create two proxies for user demo
                id1 = inst.client.startProxy("01_hello");
                Assertions.assertNotNull(id1);

                id2 = inst.client.startProxy("02_hello");
                Assertions.assertNotNull(id2);

                // 3. get defined proxies
                originalProxies1 = inst.client.getProxies();
                Assertions.assertNotNull(originalProxies1);
                Assertions.assertEquals(2, originalProxies1.size());

                // 4. create two proxies for user demo2
                id3 = clientDemo2.startProxy("01_hello");
                Assertions.assertNotNull(id3);

                id4 = clientDemo2.startProxy("02_hello");
                Assertions.assertNotNull(id4);

                // 5. get defined proxies
                originalProxies2 = clientDemo2.getProxies();
                Assertions.assertNotNull(originalProxies2);

                // 6. create two proxies for user demo3
                id5 = clientDemo3.startProxy("01_hello");
                Assertions.assertNotNull(id5);

                id6 = clientDemo3.startProxy("02_hello");
                Assertions.assertNotNull(id6);

                // 7. get defined proxies
                originalProxies3 = clientDemo3.getProxies();
                Assertions.assertNotNull(originalProxies3);
            }

            // 8. start the instance again
            try (ShinyProxyInstance inst = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), properties)) {
                ShinyProxyClient clientDemo2 = inst.getClient("demo2");
                ShinyProxyClient clientDemo3 = inst.getClient("demo3");

                // 9. get defined proxies for user demo
                HashSet<JsonObject> newProxies1 = inst.client.getProxies();
                Assertions.assertNotNull(newProxies1);

                // 10. assert that the responses are equal
                Assertions.assertEquals(originalProxies1, newProxies1);

                // 11. get defined proxies for user demo2
                HashSet<JsonObject> newProxies2 = clientDemo2.getProxies();
                Assertions.assertNotNull(newProxies2);

                // 12. assert that the responses are equal
                Assertions.assertEquals(originalProxies2, newProxies2);

                // 13. get defined proxies for user demo3
                HashSet<JsonObject> newProxies3 = clientDemo3.getProxies();
                Assertions.assertNotNull(newProxies3);

                // 14. assert that the responses are equal
                Assertions.assertEquals(originalProxies3, newProxies3);

                // 15. stop the proxies
                inst.client.stopProxy(id1);
                inst.client.stopProxy(id2);
                clientDemo2.stopProxy(id3);
                clientDemo2.stopProxy(id4);
                clientDemo3.stopProxy(id5);
                clientDemo3.stopProxy(id6);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void simple_recover_multiple_instances(String backend, Map<String, String> properties) {
        try (ContainerSetup k8s = new ContainerSetup(backend)) {
            String id1, id2;
            HashSet<JsonObject> originalProxies1, originalProxies2;
            // 1. create the first instance
            try (ShinyProxyInstance inst1 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), 7583, "demo", properties, false)) {
                // 2. create the second instance
                try (ShinyProxyInstance inst2 = new ShinyProxyInstance(String.format("application-app-recovery_%s_2.yml", backend), 7584, "demo", properties, false)) {

                    // 3. create a proxy on both instances
                    id1 = inst1.client.startProxy("01_hello");
                    Assertions.assertNotNull(id1);

                    id2 = inst2.client.startProxy("01_hello");
                    Assertions.assertNotNull(id2);

                    // 4. get defined proxies
                    originalProxies1 = inst1.client.getProxies();
                    Assertions.assertNotNull(originalProxies1);
                    Assertions.assertEquals(1, originalProxies1.size());

                    originalProxies2 = inst2.client.getProxies();
                    Assertions.assertNotNull(originalProxies2);
                    Assertions.assertEquals(1, originalProxies2.size());
                }
            }

            // 5. start both instances again
            try (ShinyProxyInstance inst1 = new ShinyProxyInstance(String.format("application-app-recovery_%s.yml", backend), 7583, "demo", properties, false)) {
                try (ShinyProxyInstance inst2 = new ShinyProxyInstance(String.format("application-app-recovery_%s_2.yml", backend), 7584, "demo", properties, false)) {
                    // 6. get defined proxies
                    HashSet<JsonObject> newProxies1 = inst1.client.getProxies();
                    Assertions.assertNotNull(newProxies1);

                    HashSet<JsonObject> newProxies2 = inst2.client.getProxies();
                    Assertions.assertNotNull(newProxies2);

                    // 7. assert that the responses are equal
                    Assertions.assertEquals(originalProxies1, newProxies1);
                    Assertions.assertEquals(originalProxies2, newProxies2);

                    // 8. stop the proxies
                    inst1.client.stopProxy(id1);
                    inst2.client.stopProxy(id2);
                }
            }
        }
    }

    @Test
    public void kubernetes_multiple_namespaces() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            String id1, id2;
            HashSet<JsonObject> originalProxies;
            // 1. create the instance
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-app-recovery_kubernetes_multi_ns.yml")) {

                // 2. create a proxy
                id1 = inst.client.startProxy("01_hello");
                Assertions.assertNotNull(id1);

                id2 = inst.client.startProxy("02_hello");
                Assertions.assertNotNull(id2);

                // 3. get defined proxies
                originalProxies = inst.client.getProxies();
                Assertions.assertNotNull(originalProxies);
            }

            // 4. start the instances again
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-app-recovery_kubernetes_multi_ns.yml")) {
                // 5. get defined proxies
                HashSet<JsonObject> newProxies = inst.client.getProxies();
                Assertions.assertNotNull(newProxies);

                // 6. assert that the responses are equal
                Assertions.assertEquals(originalProxies, newProxies);

                // 7. stop the proxies
                inst.client.stopProxy(id1);
                inst.client.stopProxy(id2);
            }
        }
    }

    @Test
    public void shutdown_should_cleanup_by_default() {
        try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
            // 1. create the instance
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-app-recovery_kubernetes_normal_shutdown.yml")) {
                // 2. create a proxy
                String id = inst.client.startProxy("01_hello");
                Assertions.assertNotNull(id);
            }

            // 3. check if pod is cleanup correctly
            Assertions.assertTrue(k8s.checkDockerIsClean());
        }
    }

}
