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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mandas.docker.client.DefaultDockerClient;
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder;
import org.mandas.docker.client.exceptions.DockerCertificateException;
import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.messages.swarm.SecretBind;
import org.mandas.docker.client.messages.swarm.SecretSpec;
import org.mandas.docker.client.messages.swarm.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

public class TestIntegrationOnSwarm {

    private static final ShinyProxyInstance inst = new ShinyProxyInstance("application-test-swarm.yml", new HashMap<>());

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void launchProxy() throws DockerCertificateException, DockerException, InterruptedException, InvalidParametersException {
        try (ContainerSetup containerSetup = new ContainerSetup("docker-swarm")) {
            try (DefaultDockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
                String id = inst.client.startProxy("01_hello");
                Proxy proxy = inst.proxyService.getProxy(id);

                List<Service> services = dockerClient.listServices();
                Assertions.assertEquals(1, services.size());
                Service service = services.get(0);
                Assertions.assertEquals("openanalytics/shinyproxy-integration-test-app", service.spec().taskTemplate().containerSpec().image());

                inst.proxyService.stopProxy(null, proxy, true).run();
            }
        }
    }

    @Test
    public void launchProxyWithSecret() throws DockerCertificateException, DockerException, InterruptedException, InvalidParametersException {
        try (ContainerSetup containerSetup = new ContainerSetup("docker-swarm")) {
            try (DefaultDockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
                String secret1Id = dockerClient.createSecret(SecretSpec.builder()
                    .data(Base64.getEncoder().encodeToString("MySecret1".getBytes(StandardCharsets.UTF_8)))
                    .name("my_secret")
                    .build()).id();

                String secret2Id = dockerClient.createSecret(SecretSpec.builder()
                    .data(Base64.getEncoder().encodeToString("MySecret2".getBytes(StandardCharsets.UTF_8)))
                    .name("my_secret_2")
                    .build()).id();

                String id = inst.client.startProxy("01_hello_secret");
                Proxy proxy = inst.proxyService.getProxy(id);

                List<Service> services = dockerClient.listServices();
                Assertions.assertEquals(1, services.size());
                Service service = services.get(0);
                Assertions.assertEquals("openanalytics/shinyproxy-integration-test-app", service.spec().taskTemplate().containerSpec().image());

                SecretBind secret1 = service.spec().taskTemplate().containerSpec().secrets().get(0);

                Assertions.assertEquals(secret1Id, secret1.secretId());
                Assertions.assertEquals("my_secret", secret1.secretName());
                Assertions.assertEquals("my_secret", secret1.file().name());
                Assertions.assertEquals("0", secret1.file().gid());
                Assertions.assertEquals("0", secret1.file().uid());
                Assertions.assertEquals(292, secret1.file().mode()); // 0444 in decimal

                SecretBind secret2 = service.spec().taskTemplate().containerSpec().secrets().get(1);

                Assertions.assertEquals(secret2Id, secret2.secretId());
                Assertions.assertEquals("my_secret_2", secret2.secretName());
                Assertions.assertEquals("/var/pass", secret2.file().name());
                Assertions.assertEquals("1000", secret2.file().gid());
                Assertions.assertEquals("1000", secret2.file().uid());
                Assertions.assertEquals(384, secret2.file().mode()); // 0444 in decimal

                inst.proxyService.stopProxy(null, proxy, true).run();
                dockerClient.deleteSecret(secret1Id);
                dockerClient.deleteSecret(secret2Id);
            }
        }
    }

}
