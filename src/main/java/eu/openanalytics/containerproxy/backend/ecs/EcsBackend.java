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

import eu.openanalytics.containerproxy.ContainerFailedToStartException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.PortMappings;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.Retrying;
import org.springframework.security.core.Authentication;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Attachment;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.Compatibility;
import software.amazon.awssdk.services.ecs.model.ContainerDefinition;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.NetworkMode;
import software.amazon.awssdk.services.ecs.model.PropagateTags;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.Task;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    protected Container startContainer(Authentication user, Container initialContainer, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ContainerFailedToStartException {
        proxyStartupLogBuilder.startingContainer(spec.getIndex());
        Container.ContainerBuilder rContainerBuilder = initialContainer.toBuilder();
        String containerId = UUID.randomUUID().toString();
        rContainerBuilder.id(containerId);
        try {
//        EcsSpecExtension specExtension = proxySpec.getSpecExtension(EcsSpecExtension.class);

        List<String> subnets = new ArrayList<>();
        int index = 0;
        String subnet = environment.getProperty(String.format("proxy.ecs.subnets[%d]", index));
        while (subnet != null) {
            subnets.add(subnet);
            index++;
            subnet = environment.getProperty(String.format("proxy.ecs.subnets[%d]", index));
        }

        List<String> securityGroups = new ArrayList<>();
        index = 0;
        String securityGroup = environment.getProperty(String.format("proxy.ecs.security-groups[%d]", index));
        while (securityGroup != null) {
            securityGroups.add(securityGroup);
            index++;
            securityGroup = environment.getProperty(String.format("proxy.ecs.security-groups[%d]", index)); // TODO + spec extension
        }

        List<Tag> tags = new ArrayList<>();
        Stream.concat(
                proxy.getRuntimeValues().values().stream(),
                initialContainer.getRuntimeValues().values().stream()
        ).forEach(runtimeValue -> {
            String value = runtimeValue.toString();
            if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
                if (validateEcsTagValue(proxy, runtimeValue.getKey().getKeyAsLabel(), value)) {
                    tags.add(Tag.builder().key(runtimeValue.getKey().getKeyAsLabel()).value(value).build());
                }
            }
        });

        for (Map.Entry<String, String> label : spec.getLabels().getValueOrDefault(new HashMap<>()).entrySet()) {
            if (validateEcsTagValue(proxy, label.getKey(), label.getValue())) {
                tags.add(Tag.builder().key(label.getKey()).value(label.getValue()).build());
            }
        }

        String taskDefinitionArn = getTaskDefinition(user, spec, proxy, initialContainer, containerId, tags);

        RunTaskResponse runTaskResponse = ecsClient.runTask(builder -> builder
            .cluster(getProperty(PROPERTY_CLUSTER))
            .count(1)
            .taskDefinition(taskDefinitionArn)
            .propagateTags(PropagateTags.TASK_DEFINITION)
            .networkConfiguration(NetworkConfiguration.builder()
                .awsvpcConfiguration(AwsVpcConfiguration.builder()
                    .subnets(subnets)
                    .securityGroups(securityGroups)
                    .build())
                .build())
            .launchType(LaunchType.FARGATE)
            .tags(tags));

        if (!runTaskResponse.hasTasks()) {
            throw new ContainerFailedToStartException("No task in taskResponse", null, rContainerBuilder.build());
        }

        String taskArn = runTaskResponse.tasks().get(0).taskArn();

        int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.ecs.service-wait-time", "180000")); // TODO
        boolean serviceReady = Retrying.retry((currentAttempt, maxAttempts) -> {
            DescribeTasksResponse response = ecsClient.describeTasks(builder -> builder
                .cluster(getProperty(PROPERTY_CLUSTER))
                .tasks(taskArn));

            if (response.hasTasks() && response.tasks().get(0).lastStatus().equals("RUNNING")) {
                return true;
            } else {
                if (currentAttempt > 10) {
                    slog.info(proxy, String.format("Container not ready yet, trying again (%d/%d)", currentAttempt, maxAttempts));
                }
                return false;
            }
        }, totalWaitMs);

        String image = ecsClient.describeTasks(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)).tasks(taskArn)).tasks().get(0).containers().get(0).image();
        rContainerBuilder.addRuntimeValue(new RuntimeValue(BackendContainerNameKey.inst, taskArn),  false);
        rContainerBuilder.addRuntimeValue(new RuntimeValue(ContainerImageKey.inst, image),  false);

        if (!serviceReady) {
            throw new ContainerFailedToStartException("Service failed to start", null, rContainerBuilder.build());
        }

        Map<Integer, Integer> portBindings = new HashMap<>();
            return setupPortMappingExistingProxy(proxy, rContainerBuilder.build(), portBindings);
        } catch (ContainerFailedToStartException t) {
            throw t;
        } catch (Throwable throwable) {
            throw new ContainerFailedToStartException("ECS container failed to start", throwable, rContainerBuilder.build());
        }
    }

    private String getTaskDefinition(Authentication user, ContainerSpec spec, Proxy proxy, Container initialContainer,  String containerId, List<Tag> tags) throws IOException {
        if (spec.getImage().getValue().startsWith("arn:aws:ecs:")) {
            // external task definition
            return spec.getImage().getValue();
        }

        List<KeyValuePair> env = buildEnv(user, spec, proxy).entrySet().stream()
            .map(v -> KeyValuePair.builder().name(v.getKey()).value(v.getValue()).build())
            .collect(Collectors.toList());

        Map<String, String> dockerLabels = spec.getLabels().getValueOrDefault(new HashMap<>());
        Stream.concat(
            proxy.getRuntimeValues().values().stream(),
            initialContainer.getRuntimeValues().values().stream()
        ).forEach(runtimeValue -> {
            if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
                dockerLabels.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
            }
        });

        RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsClient.registerTaskDefinition(builder -> builder
            .family("sp-task-definition-" + proxy.getId()) // family is a name for the task definition
            .containerDefinitions(ContainerDefinition.builder()
                .name("sp-container-" + containerId)
                .image(spec.getImage().getValue())
                .dnsServers(spec.getDns().getValueOrNull())
                .privileged(isPrivileged() || spec.isPrivileged())
                .command(spec.getCmd().getValueOrNull())
                .environment(env)
                .stopTimeout(2)
                .dockerLabels(dockerLabels)
                .build())
            .networkMode(NetworkMode.AWSVPC) // only option when using fargate
            .requiresCompatibilities(Compatibility.FARGATE)
            .cpu(spec.getCpuRequest().getValue()) // required by fargate
            .memory(spec.getMemoryRequest().getValue()) // required by fargate
            .tags(tags));

        return registerTaskDefinitionResponse.taskDefinition().taskDefinitionArn();
    }

    @Override
    protected void doStopProxy(Proxy proxy) throws Exception {
        for (Container container : proxy.getContainers()) {
            String taskArn = container.getRuntimeValue(BackendContainerNameKey.inst);
            ecsClient.stopTask(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)).task(taskArn));

            // delete is ignored if task definition does not exist, this is the case if the task definition was not created by shinyproxy
            ecsClient.deleteTaskDefinitions(builder -> builder.taskDefinitions("sp-task-definition-" + proxy.getId() + ":1"));
        }


        int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.ecs.service-wait-time", "120000"));
        List<String> stoppingState = Arrays.asList("DEACTIVATING", "STOPPING", "DEPROVISIONING", "STOPPED", "DELETED");

        boolean isInactive = Retrying.retry((currentAttempt, maxAttempts) -> {
            for (Container container : proxy.getContainers()) {
                String taskArn = container.getRuntimeValue(BackendContainerNameKey.inst);
                DescribeTasksResponse response = ecsClient.describeTasks(builder -> builder
                    .cluster(getProperty(PROPERTY_CLUSTER))
                    .tasks(taskArn));

                System.out.println(response.tasks().get(0).lastStatus());
                if (response.hasTasks() && !stoppingState.contains(response.tasks().get(0).lastStatus())) {
                    if (currentAttempt > 10) {
                        slog.info(proxy, String.format("Container not in stopping state yet, trying again (%d/%d)", currentAttempt, maxAttempts));
                    }
                    return false;
                }
            }
            return true;
        }, totalWaitMs);

        if (!isInactive) {
            slog.warn(proxy, "Container did not get into stopping state");
        }
    }

    @Override
    protected String getPropertyPrefix() {
        return PROPERTY_PREFIX;
    }

    @Override
    public List<ExistingContainerInfo> scanExistingContainers() {
        return new ArrayList<>();
    }

    protected URI calculateTarget(Container container, PortMappings.PortMappingEntry portMapping, Integer hostPort) throws Exception {
        String targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, DEFAULT_TARGET_PROTOCOL);
        String targetHostName = "";
        int targetPort;

        Task task = getTask(container).orElseThrow(() -> new ContainerFailedToStartException("Task not found while calculating target", null, container));

        Attachment attachment = task.attachments().get(0);
        for (KeyValuePair detail : attachment.details()) {
            if (detail.name().equals("privateIPv4Address")) {
                targetHostName = detail.value();
                break;
            }
            if (detail.name().equals("privateIPv6Address")) {
                targetHostName = detail.value();
                break;
            }
        }
        if (Objects.equals(targetHostName, "")) {
            throw new ContainerFailedToStartException("Could not find ip in attachment", null, container);
        }
        targetPort = portMapping.getPort();

        return new URI(String.format("%s://%s:%s%s", targetProtocol, targetHostName, targetPort, portMapping.getTargetPath()));
    }

    private Optional<Task> getTask(Container container) {
        return getTaskInfo(container).flatMap(this::getTask);
    }

    private Optional<String> getTaskInfo(Container container) {
        String taskId = container.getRuntimeObjectOrNull(BackendContainerNameKey.inst);
        if (taskId == null) {
            return Optional.empty();
        }
        return Optional.of(taskId);
    }

    private Optional<Task> getTask(String taskInfo) {
        List<Task> tasks = ecsClient.describeTasks(builder -> builder
                .cluster(getProperty(PROPERTY_CLUSTER))
                .tasks(taskInfo)
                .build()
        ).tasks();

        return Optional.ofNullable(tasks.get(0));
    }

    private static final Pattern TAG_VALUE_PATTERN = Pattern.compile("^[a-zA-Z0-9 +-=._:/@]*$");

    private boolean validateEcsTagValue(Proxy proxy, String key, String value) {
        if (value.length() > 256) {
            slog.warn(proxy, String.format("Not adding ECS tag \"%s\" because it is longer than 256 characters", key));
            return false;
        }
        if (!TAG_VALUE_PATTERN.matcher(value).matches()) {
            slog.warn(proxy, String.format("Not adding ECS tag \"%s\" because it is contains invalid characters (only a-zA-Z0-9 +-=._:/@ allowed)", key));
            return false;
        }

        return true;
    }

}
