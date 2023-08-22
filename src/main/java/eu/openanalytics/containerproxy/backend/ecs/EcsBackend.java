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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import eu.openanalytics.containerproxy.ContainerFailedToStartException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.*;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.*;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.Retrying;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import org.springframework.data.util.Pair;
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
        // TODO Auto-generated method stub
        // Needs to know when a service, task definition for the container spec already
        // exists and modifies that.

        log.info("Starting image{}, container {} for proxy {}", spec.getImage(), spec.getIndex(), proxy.getId());
        log.info("Starting task {} for proxy {}", spec.getTaskDefinition(), proxy.getId());
        proxyStartupLogBuilder.startingContainer(spec.getIndex());
        Container.ContainerBuilder rContainerBuilder = initialContainer.toBuilder();
        String containerId = UUID.randomUUID().toString();
        rContainerBuilder.id(containerId);

        List<ContainerPort> containerPorts = spec.getPortMapping().stream()
                .map(p -> new ContainerPortBuilder().withContainerPort(p.getPort()).build())
                .collect(Collectors.toList());


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
            securityGroup = environment.getProperty(String.format("proxy.ecs.security-groups[%d]", index));
        }

//        log.info(Arrays.toString(securityGroups.toArray()));
//        log.info(Arrays.toString(subnets.toArray()));

        List<Tag> tags = new ArrayList<>();

        Stream.concat(
                proxy.getRuntimeValues().values().stream(),
                initialContainer.getRuntimeValues().values().stream()
        ).forEach(runtimeValue -> {
            if (runtimeValue.getKey().getIncludeAsTag()) {
                tags.add(Tag.builder().key(runtimeValue.getKey().getKeyAsTag()).value(runtimeValue.toString()).build());
            }
        });

        ecsClient.createService(builder -> builder
                .cluster(getProperty(PROPERTY_CLUSTER))
                .desiredCount(1)
                .serviceName("sp-service-" + containerId + "-" + proxy.getId())
                .taskDefinition(spec.getTaskDefinition().toString())
                .propagateTags(PropagateTags.SERVICE)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(subnets)
                                .securityGroups(securityGroups)
                                .build())
                        .build())
                .launchType(LaunchType.FARGATE)
                .tags(tags));


        int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.ecs.service-wait-time", "120000"));
        boolean serviceReady = Retrying.retry((currentAttempt, maxAttempts) -> {
            DescribeServicesResponse response = ecsClient.describeServices(builder -> builder
                    .cluster(getProperty(PROPERTY_CLUSTER))
                    .services("sp-service-" + containerId + "-" + proxy.getId()));

            Deployment deployment = response.services().get(0).deployments().get(0);
            if (deployment.rolloutState().equals(DeploymentRolloutState.COMPLETED)) {
                return true;
            } else {
                if (currentAttempt > 10) {
                    slog.info(proxy, String.format("Container not ready yet, trying again (%d/%d)", currentAttempt, maxAttempts));
                }
                return false;
            }
        }, totalWaitMs);

        String taskArn = ecsClient.listTasks(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)).serviceName("sp-service-" + containerId + "-" + proxy.getId())).taskArns().get(0);
        String image = ecsClient.describeTasks(builder -> builder.cluster(getProperty(PROPERTY_CLUSTER)).tasks(taskArn)).tasks().get(0).containers().get(0).image();

        rContainerBuilder.addRuntimeValue(new RuntimeValue(BackendContainerNameKey.inst, taskArn),  false);
        rContainerBuilder.addRuntimeValue(new RuntimeValue(ContainerImageKey.inst, image),  false);

        if (!serviceReady) {
            throw new ContainerFailedToStartException("Service failed to start", null, rContainerBuilder.build());
        }

        Map<Integer, Integer> portBindings = new HashMap<>();
        try {
            return setupPortMappingExistingProxy(proxy, rContainerBuilder.build(), portBindings);
        } catch (ContainerFailedToStartException t) {
            throw t;
        } catch (Throwable throwable) {
            throw new ContainerFailedToStartException("ECS container failed to start", throwable, rContainerBuilder.build());
        }
    }

    @Override
    protected void doStopProxy(Proxy proxy) throws Exception {
        for (Container container : proxy.getContainers()) {
            ecsClient.updateService(builder -> builder.
                    desiredCount(0).
                    service("sp-service-" + container.getId() + "-" + proxy.getId()).
                    cluster(getProperty(PROPERTY_CLUSTER))
            );
            ecsClient.deleteService(builder -> builder.
                    service("sp-service-" + container.getId() + "-" + proxy.getId()).
                    cluster(getProperty(PROPERTY_CLUSTER))
            );
        }

        int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.ecs.service-wait-time", "120000"));

        boolean isInactive = Retrying.retry((currentAttempt, maxAttempts) -> {
            for (Container container : proxy.getContainers()) {
                DescribeServicesResponse response = ecsClient.describeServices(builder -> builder
                        .cluster(getProperty(PROPERTY_CLUSTER))
                        .services("sp-service-" + container.getId() + "-" + proxy.getId()));

                if (!response.services().get(0).status().equals("INACTIVE")) {
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
            if (detail.name().equals("privateDnsName")) {
                targetHostName = detail.value();
                break;
            }
        };
        if (Objects.equals(targetHostName, "")) {
            throw new ContainerFailedToStartException("Could not find privateDnsName in attachment", null, container);
        }
//            targetHostName = task.attachments().get(0).;
        targetPort = portMapping.getPort();

        log.info(String.format("%s://%s:%s%s", targetProtocol, targetHostName, targetPort, portMapping.getTargetPath()));

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
//        String[] tmp = taskId.split("/");
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
