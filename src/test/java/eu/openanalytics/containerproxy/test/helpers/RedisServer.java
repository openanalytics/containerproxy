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
package eu.openanalytics.containerproxy.test.helpers;


import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder;
import org.mandas.docker.client.exceptions.DockerCertificateException;
import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.messages.ContainerConfig;
import org.mandas.docker.client.messages.HostConfig;
import org.mandas.docker.client.messages.PortBinding;

import java.util.Collections;
import java.util.Map;

public class RedisServer implements AutoCloseable {

    private final String containerId;

    public RedisServer() {
        try (DockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
            dockerClient.pull("redis:7");
            containerId = dockerClient.createContainer(ContainerConfig.builder()
                .image("redis:7")
                .hostConfig(HostConfig.builder()
                    .portBindings(Map.of("6379", Collections.singletonList(PortBinding.of("127.0.0.1", "3379"))))
                    .build())
                .exposedPorts("6379")
                .build(), "redis-itest").id();
            try {
                dockerClient.startContainer(containerId);
            } catch (Throwable t) {
                dockerClient.removeContainer(containerId, DockerClient.RemoveContainerParam.forceKill());
                throw new TestHelperException("Error while setting up Redis", t);
            }
        } catch (Throwable t) {
            throw new TestHelperException("Error while setting up Redis", t);
        }
    }

    @Override
    public void close() {
        try (DockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
            dockerClient.removeContainer(containerId, DockerClient.RemoveContainerParam.forceKill());
        } catch (DockerCertificateException | DockerException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
