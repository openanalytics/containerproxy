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
package eu.openanalytics.containerproxy.backend.ecs;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.openanalytics.containerproxy.ContainerFailedToStartException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.*;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.*;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import org.springframework.security.core.Authentication;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

public class EcsBackend extends AbstractContainerBackend {

  private static final String PROPERTY_PREFIX = "proxy.ecs.";

  private static final String PROPERTY_CLUSTER = "name";
  private static final String PROPERTY_REGION = "region";

  private EcsClient ecsClient;

  @Override
  public void initialize() {
    super.initialize();

    Region region = Region.of(getProperty(PROPERTY_REGION));

    ecsClient = EcsClient.builder()
        .region(region)
        .build();
  }

  public void initialize(EcsClient client) {
    super.initialize();
    ecsClient = client;
  }


  private Map<RuntimeValueKey<?>, RuntimeValue> parseTagsAsRuntimeValues(Task task, String containerId) {
    Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = new HashMap<>();

    // tags to map
//    log.info("Getting tags for container {}", containerArn);
    Map<String, String> tags = task.tags().stream().collect(
        HashMap::new,
        (m, v) -> m.put(v.key(), v.value()),
        HashMap::putAll
    );

    for (RuntimeValueKey<?> key : RuntimeValueKeyRegistry.getRuntimeValueKeys()) {
      if (key.getIncludeAsTag()) {
        String value = tags.get(key.getKeyAsTag());
        if (value != null) {
          runtimeValues.put(key, new RuntimeValue(key, key.deserializeFromString(value)));
        }
      } else if (key.isRequired()) {
        // value is null but is required
        log.warn("Ignoring container {} because no label or annotation named {} is found", containerId, key.getKeyAsLabel());
        return null;
      }
    }

    return runtimeValues;
  }

  @Override
  public List<ExistingContainerInfo> scanExistingContainers() throws Exception {
    // TODO Auto-generated method stub

    ArrayList<ExistingContainerInfo> containers = new ArrayList<>();

    ListTasksResponse ecsTasks = ecsClient.listTasks(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)));
    List<String> taskArns = ecsTasks.taskArns();

    DescribeTasksResponse tasks = ecsClient
        .describeTasks(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)).tasks(taskArns));
    tasks.tasks().forEach(task -> {
      task.containers().forEach(container -> {

        Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseTagsAsRuntimeValues(
            task, container.runtimeId());

        if (runtimeValues == null) {
          return;
        }

        runtimeValues.put(ContainerImageKey.inst, new RuntimeValue(ContainerImageKey.inst, container.image()));
        runtimeValues.put(BackendContainerNameKey.inst, new RuntimeValue(BackendContainerNameKey.inst, getProperty(PROPERTY_CLUSTER) + "/" + container.name()));


        if (!appRecoveryService.canRecoverProxy(container.runtimeId())) {
          log.warn("Ignoring container {} because instanceId {} is not correct", container.name(), container.runtimeId());
          return;
        }

        Map<Integer, Integer> portBindings = new HashMap<>();
        for (NetworkBinding binding : container.networkBindings()) {
          Integer hostPort = binding.hostPort();
          Integer containerPort = binding.containerPort();
          portBindings.put(containerPort, hostPort);
        }

        containers.add(new ExistingContainerInfo(
            container.runtimeId(),
            runtimeValues,
            container.image(),
            portBindings));
      });
    });

    return containers;
  }


  @Override
  protected Container startContainer(Authentication user, Container initialContainer, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ContainerFailedToStartException {
    // TODO Auto-generated method stub
    // Needs to know when a service, task definition for the container spec already
    // exists and modifies that.

    Container.ContainerBuilder rContainerBuilder = initialContainer.toBuilder();
    String containerId = UUID.randomUUID().toString();
    rContainerBuilder.id(containerId);


    ecsClient.createService(builder -> builder
        .cluster(getProperty(PROPERTY_CLUSTER))
        .desiredCount(1).serviceName("sp-service-" + containerId + "-" + proxy.getId())
        .taskDefinition(spec.getTaskDefinition().toString()));

    return rContainerBuilder.build();
  }

  @Override
  protected void doStopProxy(Proxy proxy) throws Exception {
    for (Container container : proxy.getContainers()) {
      // String kubeNamespace =
      // container.getParameters().get(PARAM_NAMESPACE).toString();
      // if (kubeNamespace == null) {
      // kubeNamespace = getProperty(PROPERTY_NAMESPACE, DEFAULT_NAMESPACE);
      // }

      // Pod pod = Pod.class.cast(container.getParameters().get(PARAM_POD));
      // if (pod != null) kubeClient.pods().inNamespace(kubeNamespace).delete(pod);
      // Service service =
      // Service.class.cast(container.getParameters().get(PARAM_SERVICE));
      // if (service != null)
      // kubeClient.services().inNamespace(kubeNamespace).delete(service);

      // // delete additional manifests
      // for (HasMetadata fullObject: getAdditionManifestsAsObjects(proxy,
      // kubeNamespace)) {
      // kubeClient.resource(fullObject).delete();
      // }

//      ecsClient.deleteService(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)).service("sp-service-" + container.getId() + "-" + proxy.getId()));
      ecsClient.updateService(builder -> builder.desiredCount(0));
    }
  }

  @Override
  protected String getPropertyPrefix() {
    return PROPERTY_PREFIX;
  }

  @Override
  protected URI calculateTarget(Container container, PortMappings.PortMappingEntry portMapping, Integer hostPort) throws Exception {
    return null;
  }

}
