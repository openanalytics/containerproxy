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

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class KubernetesTestBase {

    public static interface TestBody {
        public void run(NamespacedKubernetesClient client, String namespace, String overriddenNamespace) throws Exception;
    }

    public static final String namespace = "itest";
    public static final String overriddenNamespace = "itest-overridden";
    private final List<String> managedNamespaces = Arrays.asList(namespace, overriddenNamespace);

    static protected final DefaultKubernetesClient client = new DefaultKubernetesClient();

    protected void setup(TestBody test) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::deleteNamespaces));

        deleteNamespaces();
        createNamespaces();

        try {
            Thread.sleep(1000); // wait for namespaces and tokens to become ready

            NamespacedKubernetesClient namespacedKubernetesClient = client.inNamespace(namespace);

            test.run(namespacedKubernetesClient, namespace, overriddenNamespace);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            deleteNamespaces();
        }
    }

    private void deleteNamespaces() {
        try {
            for (String namespace : managedNamespaces) {
                Namespace ns = client.namespaces().withName(namespace).get();
                if (ns == null) {
                    continue;
                }

                client.namespaces().delete(ns);

                while (client.namespaces().withName(namespace).get() != null) {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException e) {
        }
    }

    private void createNamespaces() {
        for (String namespace : managedNamespaces) {
            client.namespaces().create(new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(namespace)
                    .endMetadata()
                    .build());
        }
    }


    protected List<Secret> getSecrets(String namespace) {
        return client.secrets().inNamespace(namespace).list().getItems().stream().filter(it -> !it.getMetadata().getName().startsWith("default-token")).collect(Collectors.toList());
    }

    protected Secret getSingleSecret(String namespace) {
        List<Secret> secrets = getSecrets(namespace);
        Assertions.assertEquals(1, secrets.size());
        return secrets.get(0);
    }

}
