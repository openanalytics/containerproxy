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
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PortMappingsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.util.EnvironmentUtils;
import eu.openanalytics.containerproxy.util.Retrying;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.util.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Attachment;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.Compatibility;
import software.amazon.awssdk.services.ecs.model.ContainerDefinition;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.EFSAuthorizationConfig;
import software.amazon.awssdk.services.ecs.model.EFSVolumeConfiguration;
import software.amazon.awssdk.services.ecs.model.EphemeralStorage;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.LogConfiguration;
import software.amazon.awssdk.services.ecs.model.LogDriver;
import software.amazon.awssdk.services.ecs.model.MountPoint;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.NetworkMode;
import software.amazon.awssdk.services.ecs.model.PropagateTags;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.RuntimePlatform;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.Volume;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "proxy.container-backend", havingValue = "ecs")
public class EcsBackend extends AbstractContainerBackend {

    private static final String PROPERTY_PREFIX = "proxy.ecs.";
    private static final String PROPERTY_CLUSTER = "name";
    private static final String PROPERTY_REGION = "region";
    private static final String PROPERTY_SERVICE_WAIT_TIME = "service-wait-time";
    private static final Pattern TAG_VALUE_PATTERN = Pattern.compile("^[a-zA-Z0-9 +-=._:/@]*$");
    private static final List<RuntimeValueKey<?>> IGNORED_RUNTIME_VALUES = Collections.singletonList(PortMappingsKey.inst);

    private EcsClient ecsClient;
    private Boolean enableCloudWatch;
    private String cloudWatchGroupPrefix;
    private String cloudWatchRegion;
    private String cloudWatchStreamPrefix;
    private List<String> subnets;
    private List<String> securityGroups;
    private int totalWaitMs;
    private String cluster;

    @Inject
    private IProxySpecProvider proxySpecProvider;

    @Override
    @PostConstruct
    public void initialize() {
        super.initialize();

        Region region = Region.of(getProperty(PROPERTY_REGION));

        ecsClient = EcsClient.builder()
            .region(region)
            .build();

        cluster = getProperty(PROPERTY_CLUSTER);
        subnets = EnvironmentUtils.readList(environment, "proxy.ecs.subnets");
        securityGroups = EnvironmentUtils.readList(environment, "proxy.ecs.security-groups");
        totalWaitMs = environment.getProperty(PROPERTY_PREFIX + PROPERTY_SERVICE_WAIT_TIME, Integer.class, 180000);

        enableCloudWatch = environment.getProperty("proxy.ecs.enable-cloudwatch", Boolean.class, false);
        cloudWatchGroupPrefix = environment.getProperty("proxy.ecs.cloud-watch-group-prefix", String.class, "/ecs/");
        cloudWatchRegion = environment.getProperty("proxy.ecs.cloud-watch-region", String.class, getProperty(PROPERTY_REGION));
        cloudWatchStreamPrefix =  environment.getProperty("proxy.ecs.cloud-watch-stream-prefix", String.class, "ecs");

        if (subnets.isEmpty()) {
            throw new IllegalStateException("Error in configuration of ECS backend: need at least one subnet in proxy.ecs.subnets");
        }

        if (securityGroups.isEmpty()) {
            throw new IllegalStateException("Error in configuration of ECS backend: need at least one security group in proxy.ecs.security-groups");
        }

        for (ProxySpec spec : proxySpecProvider.getSpecs()) {
            ContainerSpec containerSpec = spec.getContainerSpecs().get(0);
            if (!containerSpec.getMemoryRequest().isOriginalValuePresent()) {
                throw new IllegalStateException(String.format("Error in configuration of specs: spec with id '%s' has non 'memory-request' configured, this is required for running on ECS fargate", spec.getId()));
            }
            if (!containerSpec.getCpuRequest().isOriginalValuePresent()) {
                throw new IllegalStateException(String.format("Error in configuration of specs: spec with id '%s' has non 'cpu-request' configured, this is required for running on ECS fargate", spec.getId()));
            }
            if (containerSpec.getMemoryLimit().isOriginalValuePresent()) {
                throw new IllegalStateException(String.format("Error in configuration of specs: spec with id '%s' has 'memory-limit' configured, this is not supported by ECS fargate", spec.getId()));
            }
            if (containerSpec.getCpuLimit().isOriginalValuePresent()) {
                throw new IllegalStateException(String.format("Error in configuration of specs: spec with id '%s' has 'cpu-limit' configured, this is not supported by ECS fargate", spec.getId()));
            }
            if (containerSpec.isPrivileged()) {
                throw new IllegalStateException(String.format("Error in configuration of specs: spec with id '%s' has 'privileged: true' configured, this is not supported by ECS fargate", spec.getId()));
            }
        }
    }

    @Override
    public Proxy startContainer(Authentication user, Container initialContainer, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ContainerFailedToStartException {
        Container.ContainerBuilder rContainerBuilder = initialContainer.toBuilder();
        String containerId = UUID.randomUUID().toString();
        rContainerBuilder.id(containerId);

        EcsSpecExtension specExtension = proxySpec.getSpecExtension(EcsSpecExtension.class);
        try {
            List<Tag> tags = new ArrayList<>();
            Stream.concat(
                    proxy.getRuntimeValues().values().stream(),
                    initialContainer.getRuntimeValues().values().stream()
                )
                .filter(v -> !IGNORED_RUNTIME_VALUES.contains(v.getKey()))
                .forEach(runtimeValue -> {
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

            String taskDefinitionArn = getTaskDefinition(user, spec, specExtension, proxy, initialContainer, containerId, tags);

            // tell the status service we are starting the pod/container
            proxyStartupLogBuilder.startingContainer(initialContainer.getIndex());
            RunTaskResponse runTaskResponse = ecsClient.runTask(builder -> builder
                .cluster(cluster)
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
                .enableExecuteCommand(specExtension.getEcsEnableExecuteCommand().getValueOrDefault(false))
                .tags(tags));

            if (!runTaskResponse.hasTasks()) {
                throw new ContainerFailedToStartException("No task in taskResponse", null, rContainerBuilder.build());
            }

            String taskArn = runTaskResponse.tasks().get(0).taskArn();

            boolean serviceReady = Retrying.retry((currentAttempt, maxAttempts) -> {
                DescribeTasksResponse response = ecsClient.describeTasks(builder -> builder
                    .cluster(cluster)
                    .tasks(taskArn));

                if (response.hasTasks() && response.tasks().get(0).lastStatus().equals("RUNNING")) {
                    return true;
                } else {
                    if (currentAttempt > 10) {
                        slog.info(proxy, String.format("ECS Task not ready yet, trying again (%d/%d)", currentAttempt, maxAttempts));
                    }
                    return false;
                }
            }, totalWaitMs);

            proxyStartupLogBuilder.containerStarted(initialContainer.getIndex());

            String image = ecsClient.describeTasks(builder -> builder.cluster(cluster).tasks(taskArn)).tasks().get(0).containers().get(0).image();
            rContainerBuilder.addRuntimeValue(new RuntimeValue(BackendContainerNameKey.inst, taskArn), false);
            rContainerBuilder.addRuntimeValue(new RuntimeValue(ContainerImageKey.inst, image), false);

            if (!serviceReady) {
                throw new ContainerFailedToStartException("Service failed to start", null, rContainerBuilder.build());
            }

            Map<Integer, Integer> portBindings = new HashMap<>();
            Container rContainer = rContainerBuilder.build();
            Map<String, URI> targets = setupPortMappingExistingProxy(proxy, rContainer, portBindings);
            return proxy.toBuilder().addTargets(targets).updateContainer(rContainer).build();
        } catch (ContainerFailedToStartException t) {
            throw t;
        } catch (Throwable throwable) {
            throw new ContainerFailedToStartException("ECS container failed to start", throwable, rContainerBuilder.build());
        }
    }

    private String getTaskDefinition(Authentication user, ContainerSpec spec, EcsSpecExtension specExtension, Proxy proxy, Container initialContainer, String containerId, List<Tag> tags) throws IOException {
        if (spec.getImage().getValue().startsWith("arn:aws:ecs:")) {
            // external task definition
            return spec.getImage().getValue();
        }

        List<KeyValuePair> env = buildEnv(user, spec, proxy).entrySet().stream()
            .map(v -> KeyValuePair.builder().name(v.getKey()).value(v.getValue()).build())
            .toList();

        Map<String, String> dockerLabels = spec.getLabels().getValueOrDefault(new HashMap<>());
        Stream.concat(
            proxy.getRuntimeValues().values().stream(),
            initialContainer.getRuntimeValues().values().stream()
        ).forEach(runtimeValue -> {
            if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
                dockerLabels.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
            }
        });

        Pair<List<Volume>, List<MountPoint>> volumes = getVolumes(spec, specExtension);

        EphemeralStorage ephemeralStorage = EphemeralStorage
            .builder()
            .sizeInGiB(specExtension.ecsEphemeralStorageSize.getValueOrDefault(21))
            .build();

       RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsClient.registerTaskDefinition(builder -> builder
            .family("sp-task-definition-" + proxy.getId()) // family is a name for the task definition
            .containerDefinitions(ContainerDefinition.builder()
                .name("sp-container-" + containerId)
                .image(spec.getImage().getValue())
                .dnsServers(spec.getDns().getValueOrNull())
                .command(spec.getCmd().getValueOrNull())
                .environment(env)
                .stopTimeout(2)
                .dockerLabels(dockerLabels)
                .logConfiguration(getLogConfiguration(proxy.getId()))
                .mountPoints(volumes.getSecond())
                .build())
            .networkMode(NetworkMode.AWSVPC) // only option when using fargate
            .requiresCompatibilities(Compatibility.FARGATE)
            .cpu(spec.getCpuRequest().getValue()) // required by fargate
            .memory(spec.getMemoryRequest().getValue()) // required by fargate
            .taskRoleArn(specExtension.ecsTaskRole.getValueOrNull())
            .executionRoleArn(specExtension.ecsExecutionRole.getValueOrNull())
            .runtimePlatform(RuntimePlatform.builder()
                .cpuArchitecture(specExtension.ecsCpuArchitecture.getValueOrNull())
                .operatingSystemFamily(specExtension.ecsOperationSystemFamily.getValueOrNull())
                .build()
            )
            .ephemeralStorage(ephemeralStorage)
            .volumes(volumes.getFirst())
            .tags(tags));

        return registerTaskDefinitionResponse.taskDefinition().taskDefinitionArn();
    }

    private LogConfiguration getLogConfiguration(String proxyId) {
        if (enableCloudWatch) {
            LogConfiguration.Builder logConfiguration = LogConfiguration.builder();
            logConfiguration.logDriver(LogDriver.AWSLOGS);
            HashMap<String, String> options = new HashMap<>();
            options.put("awslogs-group", cloudWatchGroupPrefix + "sp-" + proxyId);
            options.put("awslogs-region", cloudWatchRegion);
            options.put("awslogs-stream-prefix", cloudWatchStreamPrefix);
            options.put("awslogs-create-group", "true");
            logConfiguration.options(options);
            return logConfiguration.build();
        }
        return null;
    }

    private Pair<List<Volume>, List<MountPoint>> getVolumes(ContainerSpec spec, EcsSpecExtension specExtension) {
        List<String> volumeNames = new ArrayList<>();
        List<Volume> efsVolumeConfigurations = new ArrayList<>();
        for (EcsEfsVolume volume : specExtension.getEcsEfsVolumes()) {
            EFSVolumeConfiguration.Builder efsVolumeConfiguration = EFSVolumeConfiguration.builder();
            efsVolumeConfiguration.fileSystemId(volume.getFileSystemId().getValue());
            efsVolumeConfiguration.rootDirectory(volume.getRootDirectory().getValueOrNull());
            if (volume.getTransitEncryption().getValueOrDefault(false)) {
                efsVolumeConfiguration.transitEncryption("ENABLED");
            }
            efsVolumeConfiguration.transitEncryptionPort(volume.getTransitEncryptionPort().getValueOrNull());

            EFSAuthorizationConfig.Builder authorizationConfig = EFSAuthorizationConfig.builder();
            authorizationConfig.accessPointId(volume.getAccessPointId().getValueOrNull());
            if (volume.getEnableIam().getValueOrDefault(false)) {
                authorizationConfig.iam("ENABLED");
            }
            efsVolumeConfiguration.authorizationConfig(authorizationConfig.build());

            Volume finalVolume = Volume.builder()
                .efsVolumeConfiguration(efsVolumeConfiguration.build())
                .name(volume.getName().getValue())
                .build();

            efsVolumeConfigurations.add(finalVolume);
            volumeNames.add(volume.getName().getValue());
        }

        List<MountPoint> mountPoints = new ArrayList<>();
        if (spec.getVolumes().isPresent()) {
            for (String volume : spec.getVolumes().getValue()) {
                String[] components = volume.split(":");
                if (components.length != 2 && components.length != 3) {
                    throw new IllegalArgumentException(String.format("Invalid volume configuration: %s, did not found correct components (e.g. 'myname:/mnt' or 'myname:/mnt:readonly')", volume));
                }
                String name = components[0];
                String containerPath = components[1];
                if (!volumeNames.contains(name)) {
                    throw new IllegalArgumentException(String.format("Invalid volume configuration: %s, no corresponding EFS volume definition found", volume));
                }

                MountPoint.Builder mountPoint = MountPoint.builder();
                mountPoint.sourceVolume(name);
                mountPoint.containerPath(containerPath);

                if (components.length == 3) {
                    if (Objects.equals(components[2], "readonly")) {
                        mountPoint.readOnly(true);
                    } else {
                        throw new IllegalArgumentException(String.format("Invalid volume configuration: %s, third component must be equal to 'readonly' (or removed)", volume));
                    }
                }

                mountPoints.add(mountPoint.build());
            }
        }

        return Pair.of(efsVolumeConfigurations, mountPoints);
    }

    @Override
    protected void doStopProxy(Proxy proxy) throws Exception {
        for (Container container : proxy.getContainers()) {
            String taskArn = container.getRuntimeValue(BackendContainerNameKey.inst);
            ecsClient.stopTask(builder -> builder.cluster(cluster).task(taskArn));

            // delete is ignored if task definition does not exist, this is the case if the task definition was not created by shinyproxy
            ecsClient.deleteTaskDefinitions(builder -> builder.taskDefinitions("sp-task-definition-" + proxy.getId() + ":1"));
        }

        List<String> stoppingState = Arrays.asList("DEACTIVATING", "STOPPING", "DEPROVISIONING", "STOPPED", "DELETED");

        boolean isInactive = Retrying.retry((currentAttempt, maxAttempts) -> {
            for (Container container : proxy.getContainers()) {
                String taskArn = container.getRuntimeValue(BackendContainerNameKey.inst);
                DescribeTasksResponse response = ecsClient.describeTasks(builder -> builder
                    .cluster(cluster)
                    .tasks(taskArn));

                if (response.hasTasks() && !stoppingState.contains(response.tasks().get(0).lastStatus())) {
                    if (currentAttempt > 10) {
                        slog.info(proxy, String.format("ECS Task not in stopping state yet, trying again (%d/%d)", currentAttempt, maxAttempts));
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

        return new URI(String.format("%s://%s:%s%s", getDefaultTargetProtocol(), targetHostName, targetPort, portMapping.getTargetPath()));
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
            .cluster(cluster)
            .tasks(taskInfo)
            .build()
        ).tasks();

        return Optional.ofNullable(tasks.get(0));
    }

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
