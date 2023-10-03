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
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PortMappingsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
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
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.NetworkBinding;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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


    private Map<RuntimeValueKey<?>, RuntimeValue> parseTagsAsRuntimeValues(String taskArn, String containerId) {
        Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = new HashMap<>();

        HashMap<String, String> tags = ecsClient.listTagsForResource(builder -> builder.resourceArn(taskArn)).tags().stream().collect(
                HashMap::new,
                (m, v) -> m.put(v.key(), v.value()),
                HashMap::putAll
        );


        for (RuntimeValueKey<?> key : RuntimeValueKeyRegistry.getRuntimeValueKeys()) {
            if (key.getIncludeAsTag()) {
                String value = tags.get(key.getKeyAsTag());
                if (value != null) {
                    runtimeValues.put(key, new RuntimeValue(key, key.deserializeFromString(value)));
                } else if (key.isRequired()) {
                    // value is null but is required
                    log.warn("Ignoring container {} because no tag named {} is found!", containerId, key.getKeyAsLabel());
                    return null;
                }
            }
        }

        return runtimeValues;
    }

    @Override
    public List<ExistingContainerInfo> scanExistingContainers() {
        ArrayList<ExistingContainerInfo> containers = new ArrayList<>();

        ListTasksResponse ecsTasks = ecsClient.listTasks(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)));
        List<String> taskArns = ecsTasks.taskArns();

        DescribeTasksResponse tasks = ecsClient
                .describeTasks(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)).tasks(taskArns));
        tasks.tasks().forEach(task -> {
            task.containers().forEach(container -> {

                Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseTagsAsRuntimeValues(
                        task.taskArn(), container.runtimeId());

                if (runtimeValues == null) {
                    return;
                }

                runtimeValues.put(ContainerImageKey.inst, new RuntimeValue(ContainerImageKey.inst, container.image()));
                runtimeValues.put(BackendContainerNameKey.inst, new RuntimeValue(BackendContainerNameKey.inst, getProperty(PROPERTY_CLUSTER) + "/" + container.name()));


                String containerInstanceId = runtimeValues.get(InstanceIdKey.inst).getObject();
                if (!appRecoveryService.canRecoverProxy(containerInstanceId)) {
                    log.warn("Ignoring container {} because instanceId {} is not correct", container.runtimeId(), containerInstanceId);
                    return;
                }

                Map<Integer, Integer> portBindings = new HashMap<>();
                PortMappings portMappings = new PortMappings();
                for (NetworkBinding binding : container.networkBindings()) {
                    Integer hostPort = binding.hostPort();
                    Integer containerPort = binding.containerPort();
                    portBindings.put(containerPort, hostPort);
                    // TODO: figure out name and targetPath
                    portMappings.addPortMapping(new PortMappings.PortMappingEntry("default", containerPort, ""));
                }

                runtimeValues.put(PortMappingsKey.inst, new RuntimeValue(PortMappingsKey.inst, portMappings));

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
            if (runtimeValue.getKey().getIncludeAsTag()) {
                tags.add(Tag.builder().key(runtimeValue.getKey().getKeyAsTag()).value(runtimeValue.toString()).build());
            }
        });

            String taskDefinitionArn = getTaskDefinition(user, spec, proxy, containerId, tags);

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

            if (response.tasks().get(0).lastStatus().equals("RUNNING")) {
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

    private String getTaskDefinition(Authentication user, ContainerSpec spec, Proxy proxy, String containerId, List<Tag> tags) throws IOException {
        if (spec.getImage().getValue().startsWith("arn:aws:ecs:")) {
            // external task definition
            return spec.getImage().getValue();
        }

        List<KeyValuePair> env = buildEnv(user, spec, proxy).entrySet().stream()
            .map(v -> KeyValuePair.builder().name(v.getKey()).value(v.getValue()).build())
            .collect(Collectors.toList());

        RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsClient.registerTaskDefinition(builder -> builder
            .family("sp-task-definition-" + proxy.getId()) // family is a name for the task definition
            .containerDefinitions(ContainerDefinition.builder()
                .name("sp-container-" + containerId)
                .image(spec.getImage().getValue())
                .dnsServers(spec.getDns().getValueOrNull())
                .privileged(isPrivileged() || spec.isPrivileged())
                .command(spec.getCmd().getValueOrNull())
                .environment(env)
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

        boolean isInactive = Retrying.retry((currentAttempt, maxAttempts) -> {
            for (Container container : proxy.getContainers()) {
                String taskArn = container.getRuntimeValue(BackendContainerNameKey.inst);
                DescribeTasksResponse response = ecsClient.describeTasks(builder -> builder
                    .cluster(getProperty(PROPERTY_CLUSTER))
                    .tasks(taskArn));

                if (response.hasTasks() && !response.tasks().get(0).lastStatus().equals("STOPPED") && !response.tasks().get(0).lastStatus().equals("DELETED")) {
                    if (currentAttempt > 10) {
                        slog.info(proxy, String.format("Container not ready yet, trying again (%d/%d)", currentAttempt, maxAttempts));
                    }
                    return false;
                }
            }
            return true;
        }, totalWaitMs);

        if (!isInactive) {
            throw new Exception("Service failed to stop, still draining after" + totalWaitMs + "ms");
        }
    }

    @Override
    protected String getPropertyPrefix() {
        return PROPERTY_PREFIX;
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

}
