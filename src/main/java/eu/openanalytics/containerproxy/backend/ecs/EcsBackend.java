/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.CapacityProviderStrategyItem;
import software.amazon.awssdk.services.ecs.model.ContainerDefinition;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionFamiliesResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.NetworkBinding;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

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


  private Map<RuntimeValueKey<?>, RuntimeValue> parseLabelsAndAnnotationsAsRuntimeValues(String containerId,
                                                                                         Map<String, String> labels,
                                                                                         Map<String, String> annotations) {
    Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = new HashMap<>();
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

        Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseLabelsAndAnnotationsAsRuntimeValues(
            container.runtimeId(), null, null);

        Map<Integer, Integer> portBindings = new HashMap<>();
        for (NetworkBinding binding : container.networkBindings()) {
          Integer hostPort = binding.hostPort();
          Integer containerPort = binding.containerPort();
          portBindings.put(containerPort, hostPort);
        }

        HashMap<String, Object> parameters = new HashMap<String, Object>();
//        parameters.put(PARAM_NAMESPACE, pod.getMetadata().getNamespace());
//        parameters.put(PARAM_POD, pod);

//        if (!isUseInternalNetwork()) {
//          Service service = kubeClient.services().inNamespace(namespace).withName("sp-service-" + containerId).get();
//          parameters.put(PARAM_SERVICE, service);
//        }

        containers.add(new ExistingContainerInfo(
            container.runtimeId(),
            runtimeValues,
            container.image(),
            portBindings,
            parameters));
      });
    });

    return containers;
  }

  @Override
  public void setupPortMappingExistingProxy(Proxy proxy, Container container, Map<Integer, Integer> portBindings)
      throws Exception {


  }

  @Override
  protected Container startContainer(ContainerSpec spec, Proxy proxy) throws Exception {
    // TODO Auto-generated method stub
    // Needs to know when a service, task definition for the container spec already
    // exists and modifies that.

    Container container = new Container();
    container.setSpec(spec);
    container.setId(UUID.randomUUID().toString());

    String family = getProperty(PROPERTY_CLUSTER) + "-" + proxy.getId();

    // get the task family if it exists
    ListTaskDefinitionFamiliesResponse listTaskDefinitionFamiliesResponse = ecsClient
        .listTaskDefinitionFamilies(builder -> builder.familyPrefix(family));
    List<String> families = listTaskDefinitionFamiliesResponse.families();

    TaskDefinition taskDefinition;

    // Figure out if the task definition has been created already
    if (families.size() > 1) {
      // This shouldn't happen unless someone manually creates a task definition with
      // the same properties.
      throw new Exception("More than one task definition family found for proxy " + proxy.getId());
    } else if (families.size() == 1) {
      taskDefinition = ecsClient.describeTaskDefinition(builder -> builder.taskDefinition(family)).taskDefinition();
    } else {
      ContainerDefinition containerDefinition = ContainerDefinition.builder()
              .name(container.getId())
              .image(spec.getImage())
              .cpu(Integer.parseInt(spec.getCpuLimit()) * 1024)
              .memory(Integer.parseInt(spec.getMemoryLimit()))
              .privileged(spec.isPrivileged())
              .build();

      RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsClient
          .registerTaskDefinition(builder -> builder.containerDefinitions(containerDefinition).family(family));
      taskDefinition = registerTaskDefinitionResponse.taskDefinition();
    }

//    ListServicesResponse services = ecsClient.listServices(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)));
//    DescribeServicesResponse serviceData = ecsClient
//        .describeServices(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)).services(services.serviceArns()));

//    for (String service : services.serviceArns()) {
//
//    }

    ecsClient.createService(builder -> builder
        .cluster(getProperty(PROPERTY_CLUSTER))
        .desiredCount(1).serviceName("sp-service-" + container.getId() + "-" + proxy.getId())
        .taskDefinition(taskDefinition.taskDefinitionArn()));

    return container;
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

}
