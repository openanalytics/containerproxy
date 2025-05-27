/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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

import com.google.common.base.Throwables;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mandas.docker.client.DefaultDockerClient;
import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder;
import org.mandas.docker.client.exceptions.DockerCertificateException;
import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.messages.Container;
import org.mandas.docker.client.messages.ContainerInfo;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestIntegrationOnDocker {

    private static final ShinyProxyInstance inst = new ShinyProxyInstance("application-test-docker.yml", new HashMap<>());

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void testMemorySpecification() throws DockerCertificateException, DockerException, InterruptedException, InvalidParametersException {
        try (ContainerSetup containerSetup = new ContainerSetup("docker")) {
            try (DefaultDockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
                String id = inst.client.startProxy("01_hello_memory1");
                Proxy proxy = inst.proxyService.getProxy(id);

                List<Container> containers = dockerClient.listContainers(DockerClient.ListContainersParam.withStatusRunning(), DockerClient.ListContainersParam.withLabel("openanalytics.eu/sp-proxied-app"));
                Container container = containers.getFirst();
                Assertions.assertEquals(1, containers.size());
                Assertions.assertEquals("openanalytics/shinyproxy-integration-test-app", container.image());
                ContainerInfo containerInfo = dockerClient.inspectContainer(container.id());
                Assertions.assertEquals(268435456, containerInfo.hostConfig().memoryReservation());
                Assertions.assertEquals(1073741824, containerInfo.hostConfig().memory());

                inst.proxyService.stopProxy(null, proxy, true).run();
            }
        }
    }

    @Test
    public void testInvalidMemorySpecification1() throws DockerCertificateException, DockerException, InterruptedException, InvalidParametersException {
        try (ContainerSetup containerSetup = new ContainerSetup("docker")) {
            try (DefaultDockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
                Authentication auth = UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of(new SimpleGrantedAuthority("ROLE_GROUP1")));
                ProxySpec spec = inst.specProvider.getSpec("01_hello_memory2");
                ContainerProxyException ex = Assertions.assertThrows(ContainerProxyException.class, () -> {
                    inst.proxyService.startProxy(auth, spec, null, UUID.randomUUID().toString(), Map.of()).run();
                });
                Assertions.assertEquals("Invalid memory argument: 256Mik", Throwables.getRootCause(ex).getMessage());
            }
        }
    }

    @Test
    public void testDecimalMemorySpecification() throws DockerCertificateException, DockerException, InterruptedException, InvalidParametersException {
        try (ContainerSetup containerSetup = new ContainerSetup("docker")) {
            try (DefaultDockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
                String id = inst.client.startProxy("01_hello_memory3");
                Proxy proxy = inst.proxyService.getProxy(id);

                List<Container> containers = dockerClient.listContainers(DockerClient.ListContainersParam.withStatusRunning(), DockerClient.ListContainersParam.withLabel("openanalytics.eu/sp-proxied-app"));
                Container container = containers.getFirst();
                Assertions.assertEquals(1, containers.size());
                Assertions.assertEquals("openanalytics/shinyproxy-integration-test-app", container.image());
                ContainerInfo containerInfo = dockerClient.inspectContainer(container.id());
                Assertions.assertEquals(536870912, containerInfo.hostConfig().memoryReservation());

                inst.proxyService.stopProxy(null, proxy, true).run();
            }
        }
    }

    @Test
    public void testInvalidMemorySpecification2() throws DockerCertificateException, DockerException, InterruptedException, InvalidParametersException {
        try (ContainerSetup containerSetup = new ContainerSetup("docker")) {
            try (DefaultDockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
                Authentication auth = UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of(new SimpleGrantedAuthority("ROLE_GROUP1")));
                ProxySpec spec = inst.specProvider.getSpec("01_hello_memory4");
                ContainerProxyException ex = Assertions.assertThrows(ContainerProxyException.class, () -> {
                    inst.proxyService.startProxy(auth, spec, null, UUID.randomUUID().toString(), Map.of()).run();
                });
                Assertions.assertEquals("Invalid memory argument: 0,5Gi, no ',' allowed in number", Throwables.getRootCause(ex).getMessage());
            }
        }
    }

}
