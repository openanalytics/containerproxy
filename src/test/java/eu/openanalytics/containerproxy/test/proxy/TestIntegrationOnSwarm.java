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

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.swarm.SecretBind;
import com.spotify.docker.client.messages.swarm.SecretSpec;
import com.spotify.docker.client.messages.swarm.Service;
import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@SpringBootTest(classes = {TestIntegrationOnSwarm.TestConfiguration.class, ContainerProxyApplication.class})
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
@ActiveProfiles("test-swarm")
public class TestIntegrationOnSwarm {

    @Inject
    private Environment environment;

    @Inject
    private ProxyService proxyService;

    private boolean checkEverythingCleanedUp() throws DockerCertificateException, DockerException, InterruptedException {
        try (DefaultDockerClient dockerClient = DefaultDockerClient.fromEnv().build()) {

            return dockerClient.listContainers().stream()
                    .filter(it -> it.labels() != null && it.labels().containsKey("openanalytics.eu/sp-proxied-app"))
                    .count() == 0
                    && dockerClient.listServices().size() == 0;
        }
    }

    @AfterEach
    public void waitForCleanup() throws InterruptedException, DockerException, DockerCertificateException {
        Thread.sleep(3_000); // wait before checking
        for (int i = 0; i < 120; i++) {
            if (checkEverythingCleanedUp()) {
                break;
            }
            Thread.sleep(1_000);
        }
        Assertions.assertTrue(checkEverythingCleanedUp());
    }

    @BeforeEach
    public void beforeEach() throws DockerCertificateException, DockerException, InterruptedException {
        Assertions.assertTrue(checkEverythingCleanedUp());
    }

    @Test
    public void launchProxy() throws DockerCertificateException, DockerException, InterruptedException, InvalidParametersException {
        try (DefaultDockerClient dockerClient = DefaultDockerClient.fromEnv().build()) {
            String specId = environment.getProperty("proxy.specs[0].id");

            ProxySpec spec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            Proxy proxy = proxyService.startProxy(spec);

            List<Service> services = dockerClient.listServices();
            Assertions.assertEquals(1, services.size());
            Service service = services.get(0);
            Assertions.assertEquals("openanalytics/shinyproxy-demo", service.spec().taskTemplate().containerSpec().image());

            proxyService.stopProxy(null, proxy, true).run();
        }
    }

    @Test
    public void launchProxyWithSecret() throws DockerCertificateException, DockerException, InterruptedException, InvalidParametersException {
        try (DefaultDockerClient dockerClient = DefaultDockerClient.fromEnv().build()) {
            String secret1Id = dockerClient.createSecret(SecretSpec.builder()
                    .data(Base64.getEncoder().encodeToString("MySecret1".getBytes(StandardCharsets.UTF_8)))
                    .name("my_secret")
                    .build()).id();

            String secret2Id = dockerClient.createSecret(SecretSpec.builder()
                    .data(Base64.getEncoder().encodeToString("MySecret2".getBytes(StandardCharsets.UTF_8)))
                    .name("my_secret_2")
                    .build()).id();

            String specId = environment.getProperty("proxy.specs[1].id");

            ProxySpec spec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
            Proxy proxy = proxyService.startProxy(spec);

            List<Service> services = dockerClient.listServices();
            Assertions.assertEquals(1, services.size());
            Service service = services.get(0);
            Assertions.assertEquals("openanalytics/shinyproxy-demo", service.spec().taskTemplate().containerSpec().image());

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

            proxyService.stopProxy(null, proxy, true).run();
            dockerClient.deleteSecret(secret1Id);
            dockerClient.deleteSecret(secret2Id);
        }
    }


    public static class TestConfiguration {

        @Bean
        @Primary
        public ProxyMappingManager mappingManager() {
            return new TestIntegrationOnKube.NoopMappingManager();
        }

        @Bean
        @Primary
        public UserService mockedUserService() {
            return new MockedUserService();
        }

    }

}
