package eu.openanalytics.containerproxy.backend.ecs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.NetworkBinding;
import software.amazon.awssdk.services.ecs.model.Container;

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

        Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseLabelsAndAnnotationsAsRuntimeValues(containerId,
            labels, annotations);
        if (runtimeValues == null) {
          continue;
        }

        Map<Integer, Integer> portBindings = new HashMap<>();
        for (NetworkBinding binding : container.networkBindings()) {
          Integer hostPort = binding.hostPort();
          Integer containerPort = binding.containerPort();
          portBindings.put(containerPort, hostPort);
        }

        HashMap<String, Object> parameters = new HashMap();
        parameters.put(PARAM_NAMESPACE, pod.getMetadata().getNamespace());
        parameters.put(PARAM_POD, pod);

        if (!isUseInternalNetwork()) {
          Service service = kubeClient.services().inNamespace(namespace).withName("sp-service-" + containerId).get();
          parameters.put(PARAM_SERVICE, service);
        }

        containers.add(new ExistingContainerInfo(
            container.runtimeId(),
            runtimeValues,
            container.image(),
            portBindings,
            parameters

        ));
      });
    });

    return null;
  }

  @Override
  public void setupPortMappingExistingProxy(Proxy proxy, Container container, Map<Integer, Integer> portBindings)
      throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  protected Container startContainer(ContainerSpec spec, Proxy proxy) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  protected void doStopProxy(Proxy proxy) throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  protected String getPropertyPrefix() {
    return PROPERTY_PREFIX;
  }

}
