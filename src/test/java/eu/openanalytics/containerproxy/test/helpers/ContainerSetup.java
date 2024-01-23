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
package eu.openanalytics.containerproxy.test.helpers;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import eu.openanalytics.containerproxy.util.Retrying;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.List;

public class ContainerSetup implements AutoCloseable {

    public final String namespace = "itest";
    public final String overriddenNamespace = "itest-overridden";
    private final List<String> managedNamespaces = Arrays.asList(namespace, overriddenNamespace);
    public final DefaultKubernetesClient client = new DefaultKubernetesClient();
    public final NamespacedKubernetesClient namespacedClient;
    private final String backend;

    public ContainerSetup(String backend) {
        this.backend = backend;
        try {
            if (backend.equals("kubernetes")) {
                // set up Kubernetes
                deleteNamespaces();
                createNamespaces();

                TestUtil.sleep(1000); // wait for namespaces and tokens to become ready
                namespacedClient = client.inNamespace(namespace);
            } else {
                namespacedClient = null;
            }

            // check docker
            if (!checkDockerIsClean()) {
                throw new TestHelperException("Docker not clean before starting test");
            }
        } catch (Throwable t) {
            throw new TestHelperException("Error while setting up kubernetes", t);
        }
    }

    private void deleteNamespaces() {
        if (!backend.equals("kubernetes")) {
            return;
        }
        try {
            for (String namespace : managedNamespaces) {
                Namespace ns = client.namespaces().withName(namespace).get();
                if (ns == null) {
                    continue;
                }
                client.namespaces().withName(namespace).delete();
            }
            for (String namespace : managedNamespaces) {
                Retrying.retry((c, m) -> client.namespaces().withName(namespace).get() == null, 60_000, "namespace deletion: " + namespace, 1, true);
            }
        } catch (Throwable t) {
            throw new TestHelperException("Error while cleaning kubernetes", t);
        }
    }

    private void createNamespaces() {
        for (String namespace : managedNamespaces) {
            client.namespaces().create(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build());
        }
    }

    public List<Secret> getSecrets(String namespace) {
        return client.secrets().inNamespace(namespace).list().getItems().stream().filter(it -> !it.getMetadata().getName().startsWith("default-token")).toList();
    }

    public Secret getSingleSecret(String namespace) {
        List<Secret> secrets = getSecrets(namespace);
        Assertions.assertEquals(1, secrets.size());
        return secrets.get(0);
    }

    public boolean checkDockerIsClean() {
        return Retrying.retry((c, m) -> {
            try {
                switch (backend) {
                    case "docker" -> {
                        DefaultDockerClient dockerClient = DefaultDockerClient.fromEnv().build();
                        long count = dockerClient.listContainers().stream().filter(it -> it.labels() != null && it.labels().containsKey("openanalytics.eu/sp-proxied-app")).count();
                        return count <= 0;
                    }
                    case "docker-swarm" -> {
                        DefaultDockerClient dockerClient = DefaultDockerClient.fromEnv().build();
                        return dockerClient.listServices().isEmpty();
                    }
                    case "kubernetes" -> {
                        List<Pod> pods1 = client.pods().inNamespace(namespace).list().getItems();
                        List<Pod> pods2 = client.pods().inNamespace(overriddenNamespace).list().getItems();
                        return pods1.isEmpty() && pods2.isEmpty();
                    }
                }
                return true;
            } catch (DockerException | InterruptedException | DockerCertificateException e) {
                throw new TestHelperException("Error while checking if Docker is clean", e);
            }
        }, 60_000, "docker/swarm/k8s is clean", 1, true);
    }


    @Override
    public void close() {
        deleteNamespaces();
        if (!checkDockerIsClean()) {
            throw new TestHelperException("Docker not clean after executing test");
        }
    }

}
