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
package eu.openanalytics.containerproxy.backend.kubernetes;

import com.google.common.collect.Streams;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.util.Sha1;
import io.fabric8.kubernetes.api.model.APIResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Deletable;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KubernetesManifestsRemover {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final KubernetesClient kubeClient;

    private final Map<ResourceDefinitionContext, List<String>> supportedResourcesInNamespaces = new HashMap<>();

    private final String realmId;

    public KubernetesManifestsRemover(KubernetesClient kubeClient, HashSet<String> namespaces, IdentifierService identifierService) {
        this.kubeClient = kubeClient;
        realmId = identifierService.realmId;
        for (ResourceDefinitionContext resourceDefinition : getSupportedResources()) {
            List<String> supportedNamespaces = new ArrayList<>();
            for (String namespace : namespaces) {
                try {
                    kubeClient.genericKubernetesResources(resourceDefinition).inNamespace(namespace).list();
                    logger.info("Kubernetes additional manifests is supported for resource [Group:{} Version:{} Kind:{}] in namespace: {}",
                            resourceDefinition.getGroup(), resourceDefinition.getVersion(), resourceDefinition.getKind(), namespace);
                    supportedNamespaces.add(namespace);
                } catch (KubernetesClientException ex) {
                    // no access or invalid resource -> ignore
                }
            }
            supportedResourcesInNamespaces.put(resourceDefinition, supportedNamespaces);
        }
    }

    private Set<ResourceDefinitionContext> getSupportedResources() {
        return Streams.concat(
                kubeClient.getApiGroups().getGroups().stream().flatMap(g -> g.getVersions().stream()
                        .flatMap(r -> getApiResources(r.getGroupVersion())
                                .map(apiResource -> ResourceDefinitionContext.fromApiResource(r.getGroupVersion(), apiResource))
                        )
                ),
                getApiResources("v1").map(apiResource -> ResourceDefinitionContext.fromApiResource("v1", apiResource))
        ).collect(Collectors.toSet());
    }

    private Stream<APIResource> getApiResources(String groupVersion) {
        try {
            return kubeClient.getApiResources(groupVersion)
                    .getResources()
                    .stream()
                    .filter(apiResource -> !apiResource.getName().contains("/")
                            && apiResource.getVerbs().contains("get")
                            && apiResource.getVerbs().contains("list"));
        } catch (KubernetesClientException ex) {
            // no access or invalid resource -> ignore
            return Stream.empty();
        }
    }

    public void deleteAdditionalManifests(String specId, String userId) {
        String manifestId = getManifestId(specId, userId);
        for (Map.Entry<ResourceDefinitionContext, List<String>> entry : supportedResourcesInNamespaces.entrySet()) {
            ResourceDefinitionContext resourceDefinition = entry.getKey();
            for (String namespace : entry.getValue()) {
                kubeClient.genericKubernetesResources(resourceDefinition)
                        .inNamespace(namespace)
                        .withLabel("openanalytics.eu/sp-additional-manifest", "true")
                        .withLabel("openanalytics.eu/sp-persistent-manifest", "false")
                        .withLabel("openanalytics.eu/sp-manifest-id", manifestId)
                        .resources()
                        .forEach(Deletable::delete);

            }
        }
    }

    public String getManifestId(String specId, String userId) {
        String id = String.format("%s-shinyproxy-%s-shinyproxy-%s", realmId, specId, userId);
        return Sha1.hash(id);
    }

}
