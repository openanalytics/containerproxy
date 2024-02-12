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
package eu.openanalytics.containerproxy.test.proxy;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class TestIntegration {

    @Test
    public void test_resource_name_docker() {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-docker.yml")) {
            try (ContainerSetup docker = new ContainerSetup("docker")) {
                String id1 = inst.client.startProxy("01_hello");
                Assertions.assertNotNull(id1);
                Proxy proxy = inst.proxyService.getProxy(id1);

                Assertions.assertEquals("sp-container-" + proxy.getId() + "-0", proxy.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst));
                inst.client.stopProxy(id1);

                String id2 = inst.client.startProxy("custom_resource_name");
                Assertions.assertNotNull(id2);
                Proxy proxy2 = inst.proxyService.getProxy(id2);

                Assertions.assertEquals("my-app-custom_resource_name-demo", proxy2.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst));
                inst.client.stopProxy(id2);
            }
        }
    }

    @Test
    public void test_resource_name_docker_swarm() {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-docker-swarm.yml")) {
            try (ContainerSetup swarm = new ContainerSetup("docker-swarm")) {
                String id1 = inst.client.startProxy("01_hello");
                Assertions.assertNotNull(id1);
                Proxy proxy1 = inst.proxyService.getProxy(id1);

                Assertions.assertEquals("sp-service-" + proxy1.getId() + "-0", proxy1.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst));
                inst.client.stopProxy(id1);

                String id2 = inst.client.startProxy("custom_resource_name");
                Assertions.assertNotNull(id2);
                Proxy proxy2 = inst.proxyService.getProxy(id2);

                Assertions.assertEquals("my-app-custom_resource_name-demo", proxy2.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst));
                inst.client.stopProxy(id2);
            }
        }
    }

    @Test
    public void test_resource_name_k8s() {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-kubernetes.yml")) {
            try (ContainerSetup k8s = new ContainerSetup("kubernetes")) {
                String id1 = inst.client.startProxy("01_hello");
                Assertions.assertNotNull(id1);
                Proxy proxy1 = inst.proxyService.getProxy(id1);

                Assertions.assertEquals("itest/sp-pod-" + proxy1.getId() + "-0", proxy1.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst));
                inst.client.stopProxy(id1);

                String id2 = inst.client.startProxy("custom-resource-name");
                Assertions.assertNotNull(id2);
                Proxy proxy2 = inst.proxyService.getProxy(id2);

                Assertions.assertEquals("itest/my-app-custom-resource-name-demo", proxy2.getContainers().get(0).getRuntimeObjectOrNull(BackendContainerNameKey.inst));
                inst.client.stopProxy(id2);
            }
        }
    }

}
