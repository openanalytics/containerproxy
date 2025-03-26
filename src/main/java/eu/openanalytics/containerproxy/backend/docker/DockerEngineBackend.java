/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
package eu.openanalytics.containerproxy.backend.docker;

import eu.openanalytics.containerproxy.ContainerFailedToStartException;
import eu.openanalytics.containerproxy.event.NewProxyEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.PortMappings;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerName;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.DockerDeviceRequest;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.LogStream;
import org.mandas.docker.client.exceptions.ConflictException;
import org.mandas.docker.client.exceptions.ContainerNotFoundException;
import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.exceptions.DockerRequestException;
import org.mandas.docker.client.exceptions.NotFoundException;
import org.mandas.docker.client.messages.AttachedNetwork;
import org.mandas.docker.client.messages.ContainerConfig;
import org.mandas.docker.client.messages.ContainerCreation;
import org.mandas.docker.client.messages.ContainerInfo;
import org.mandas.docker.client.messages.ContainerState;
import org.mandas.docker.client.messages.HostConfig;
import org.mandas.docker.client.messages.PortBinding;
import org.mandas.docker.client.messages.RegistryAuth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "proxy.container-backend", havingValue = "docker", matchIfMissing = true)
public class DockerEngineBackend extends AbstractDockerBackend {

    private static final String PROPERTY_IMG_PULL_POLICY = "image-pull-policy";
    private static final String PROPERTY_CONTAINER_NETWORK = "default-container-network";

    private ImagePullPolicy imagePullPolicy;
    private String nonInternalNetworkTargetProtocol;
    private URL hostURL;
    private String containerNetwork;

    @PostConstruct
    public void initialize() {
        super.initialize();
        imagePullPolicy = environment.getProperty(getPropertyPrefix() + PROPERTY_IMG_PULL_POLICY, ImagePullPolicy.class, ImagePullPolicy.IfNotPresent);
        containerNetwork = environment.getProperty(getPropertyPrefix() + PROPERTY_CONTAINER_NETWORK);

        try {
            hostURL = new URL(getProperty(PROPERTY_URL, DEFAULT_TARGET_URL));
            nonInternalNetworkTargetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, hostURL.getProtocol());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Proxy startContainer(Authentication user, Container initialContainer, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ContainerFailedToStartException {
        Container.ContainerBuilder rContainerBuilder = initialContainer.toBuilder();

        try {
            HostConfig.Builder hostConfigBuilder = HostConfig.builder();

            if (imagePullPolicy == ImagePullPolicy.Always
                || (imagePullPolicy == ImagePullPolicy.IfNotPresent && !isImagePresent(spec))) {
                slog.info(proxy, String.format("Pulling image %s", spec.getImage().getValue()));
                proxyStartupLogBuilder.pullingImage(initialContainer.getIndex());
                pullImage(spec);
                proxyStartupLogBuilder.imagePulled(initialContainer.getIndex());
            }

            Map<String, List<PortBinding>> dockerPortBindings = new HashMap<>(); // portBindings for Docker API
            Map<Integer, Integer> portBindings = new HashMap<>();
            if (isUseInternalNetwork()) {
                // In internal networking mode, we can access container ports directly, no need to bind on host.
            } else {
                // Allocate ports on the docker host to proxy to.
                for (eu.openanalytics.containerproxy.model.spec.PortMapping portMapping : spec.getPortMapping()) {
                    int hostPort = portAllocator.allocate(portRangeFrom, portRangeTo, proxy.getId());
                    dockerPortBindings.put(portMapping.getPort().toString(), Collections.singletonList(PortBinding.of("0.0.0.0", hostPort)));
                    portBindings.put(portMapping.getPort(), hostPort);
                }
            }
            hostConfigBuilder.portBindings(dockerPortBindings);

            hostConfigBuilder.memoryReservation(memoryToBytes(spec.getMemoryRequest().getValueOrNull()));
            hostConfigBuilder.memory(memoryToBytes(spec.getMemoryLimit().getValueOrNull()));
            if (spec.getCpuLimit().isPresent()) {
                // Workaround, see https://github.com/spotify/docker-client/issues/959
                long period = 100000;
                long quota = (long) (period * Float.parseFloat(spec.getCpuLimit().getValue()));
                hostConfigBuilder.cpuPeriod(period);
                hostConfigBuilder.cpuQuota(quota);
            }

            if (spec.getNetwork().isPresent()) {
                hostConfigBuilder.networkMode(spec.getNetwork().getValueAsString());
            } else if (containerNetwork != null) {
                hostConfigBuilder.networkMode(containerNetwork);
            }
            spec.getDns().ifPresent(hostConfigBuilder::dns);
            spec.getVolumes().ifPresent(hostConfigBuilder::binds);
            hostConfigBuilder.privileged(isPrivileged() || spec.isPrivileged());
            spec.getDockerIpc().ifPresent(hostConfigBuilder::ipcMode);

            List<HostConfig.DeviceRequest> deviceRequests = new ArrayList<>();
            for (DockerDeviceRequest deviceRequest : spec.getDockerDeviceRequests()) {
                HostConfig.DeviceRequest.Builder builder = HostConfig.DeviceRequest.builder();
                deviceRequest.getDriver().ifPresent(builder::driver);
                deviceRequest.getCount().ifPresent(builder::count);
                deviceRequest.getDeviceIds().ifPresent(builder::deviceIds);
                deviceRequest.getCapabilities().ifPresent(builder::capabilities);
                deviceRequest.getOptions().ifPresent(builder::options);
                deviceRequests.add(builder.build());
            }
            hostConfigBuilder.deviceRequests(deviceRequests);

            spec.getDockerRuntime().ifPresent(hostConfigBuilder::runtime);

            Map<String, String> labels = spec.getLabels().getValueOrDefault(new HashMap<>());

            Stream.concat(
                proxy.getRuntimeValues().values().stream(),
                initialContainer.getRuntimeValues().values().stream()
            ).forEach(runtimeValue -> {
                if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
                    labels.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.toString());
                }
            });

            ContainerConfig containerConfig = ContainerConfig.builder()
                .hostConfig(hostConfigBuilder.build())
                .image(spec.getImage().getValue())
                .labels(labels)
                .exposedPorts(dockerPortBindings.keySet())
                .cmd(spec.getCmd().getValueOrNull())
                .env(convertEnv(buildEnv(user, spec, proxy)))
                .user(spec.getDockerUser().getValueOrNull())
                .build();

            proxyStartupLogBuilder.startingContainer(initialContainer.getIndex());
            String containerName = spec.getResourceName().getValueOrDefault("sp-container-" + proxy.getId() + "-" + initialContainer.getIndex());
            rContainerBuilder.addRuntimeValue(new RuntimeValue(BackendContainerNameKey.inst, new BackendContainerName(containerName)), false);
            applicationEventPublisher.publishEvent(new NewProxyEvent(proxy.toBuilder().updateContainer(rContainerBuilder.build()).build(), user));

            ContainerCreation containerCreation = dockerClient.createContainer(containerConfig, containerName);
            rContainerBuilder.id(containerCreation.id());

            if (spec.getNetworkConnections().isPresent()) {
                for (String networkConnection : spec.getNetworkConnections().getValue()) {
                    dockerClient.connectToNetwork(containerCreation.id(), networkConnection);
                }
            }

            dockerClient.startContainer(containerCreation.id());
            proxyStartupLogBuilder.containerStarted(initialContainer.getIndex());

            Container rContainer = rContainerBuilder.build();
            Map<String, URI> targets = setupPortMappingExistingProxy(proxy, rContainer, portBindings);

            return proxy.toBuilder().addTargets(targets).updateContainer(rContainer).build();
        } catch (Throwable throwable) {
            throw new ContainerFailedToStartException("Docker container failed to start", throwable, rContainerBuilder.build());
        }
    }

    @Override
    protected URI calculateTarget(Container container, PortMappings.PortMappingEntry portMapping, Integer hostPort) throws Exception {
        String targetProtocol;
        String targetHostName;
        String targetPort;

        if (isUseInternalNetwork()) {
            targetProtocol = getDefaultTargetProtocol();

            // For internal networks, DNS resolution by name only works with custom names.
            // See comments on https://github.com/docker/for-win/issues/1009
            ContainerInfo info = dockerClient.inspectContainer(container.getId());
            targetHostName = info.config().hostname();

            targetPort = String.valueOf(portMapping.getPort());
        } else {
            targetProtocol = nonInternalNetworkTargetProtocol;
            targetHostName = hostURL.getHost();
            targetPort = String.valueOf(hostPort);
        }
        return new URI(String.format("%s://%s:%s%s", targetProtocol, targetHostName, targetPort, portMapping.getTargetPath()));
    }

    @Override
    protected void doStopProxy(Proxy proxy) throws Exception {
        if (proxy.getContainers().isEmpty()) {
            // containers not yet created, do no perform cleanup, see #33102
            return;
        }
        for (Container container : proxy.getContainers()) {
            if (container.getId() == null) {
                continue;
            }
            try {
                ContainerInfo containerInfo = dockerClient.inspectContainer(container.getId());
                if (containerInfo != null && containerInfo.networkSettings() != null
                    && containerInfo.networkSettings().networks() != null) {
                    for (AttachedNetwork network : containerInfo.networkSettings().networks().values()) {
                        try {
                            dockerClient.disconnectFromNetwork(container.getId(), network.networkId());
                        } catch (DockerRequestException ex) {
                            // ignore, network is already disconnected
                        }
                    }
                }
                dockerClient.removeContainer(container.getId(), DockerClient.RemoveContainerParam.forceKill());
                releasePort(proxy.getId());
            } catch (ContainerNotFoundException e) {
                releasePort(proxy.getId());
                // ignore, container is already removed
            } catch (ConflictException e) {
                // ignore, container is currently being removed
                // do not release port now
            }
        }
    }

    @Override
    public List<ExistingContainerInfo> scanExistingContainers() throws Exception {
        ArrayList<ExistingContainerInfo> containers = new ArrayList<>();

        for (org.mandas.docker.client.messages.Container container : dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())) {
            if (!container.state().equalsIgnoreCase("running")) {
                log.warn("Ignoring container {} because it is not running, {}", container.id(), container.state());
                continue; // not recovering stopped/broken apps
            }

            Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = parseLabelsAsRuntimeValues(container.id(), container.labels());
            if (runtimeValues == null) {
                continue;
            }
            runtimeValues.put(ContainerImageKey.inst, new RuntimeValue(ContainerImageKey.inst, container.image()));
            runtimeValues.put(BackendContainerNameKey.inst, new RuntimeValue(BackendContainerNameKey.inst, new BackendContainerName(container.id())));

            // add ports to PortAllocator (even if we don't recover the proxy)
            for (org.mandas.docker.client.messages.Container.PortMapping portMapping : container.ports()) {
                portAllocator.addExistingPort(runtimeValues.get(UserIdKey.inst).getObject(), portMapping.publicPort());
            }

            String containerInstanceId = runtimeValues.get(InstanceIdKey.inst).getObject();
            if (!appRecoveryService.canRecoverProxy(containerInstanceId)) {
                log.warn("Ignoring container {} because instanceId {} is not correct", container.id(), containerInstanceId);
                continue;
            }

            Map<Integer, Integer> portBindings = new HashMap<>();
            for (org.mandas.docker.client.messages.Container.PortMapping portMapping : container.ports()) {
                int hostPort = portMapping.publicPort();
                int containerPort = portMapping.privatePort();
                portBindings.put(containerPort, hostPort);
            }

            containers.add(new ExistingContainerInfo(container.id(), runtimeValues, container.image(), portBindings));

        }

        return containers;
    }

    @Override
    public boolean isProxyHealthy(Proxy proxy) {
        for (Container container : proxy.getContainers()) {
            try {
                ContainerInfo info = dockerClient.inspectContainer(container.getId());
                ContainerState state = info.state();
                if (!state.running() || !state.status().equals("running")) {
                    slog.warn(proxy, "Docker container failed: container not running, state reported by docker: " + toJson(state));
                    return false;
                }
                return true;
            } catch (ContainerNotFoundException e) {
                slog.warn(proxy, "Docker container failed: container does not exist");
                return false;
            } catch (DockerException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    @Override
    public BiConsumer<OutputStream, OutputStream> getOutputAttacher(Proxy proxy) {
        Container c = getPrimaryContainer(proxy);
        if (c == null) return null;

        return (stdOut, stdErr) -> {
            try {
                LogStream logStream = dockerClient.logs(c.getId(), DockerClient.LogsParam.follow(), DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr());
                logStream.attach(stdOut, stdErr);
            } catch (ClosedChannelException ignored) {
            } catch (IOException | InterruptedException | DockerException e) {
                log.error("Error while attaching to container output", e);
            }
        };
    }

    private boolean isImagePresent(ContainerSpec spec) throws DockerException, InterruptedException {
        try {
            dockerClient.inspectImage(spec.getImage().getValue());
            return true;
        } catch (NotFoundException ex) {
            return false;
        }
    }

    private void pullImage(ContainerSpec spec) throws DockerException, InterruptedException {
        if (spec.getDockerRegistryDomain() != null
            && spec.getDockerRegistryUsername() != null
            && spec.getDockerRegistryPassword() != null) {

            RegistryAuth registryAuth = RegistryAuth.builder()
                .serverAddress(spec.getDockerRegistryDomain())
                .username(spec.getDockerRegistryUsername())
                .password(spec.getDockerRegistryPassword())
                .build();
            dockerClient.pull(spec.getImage().getValue(), registryAuth, message -> {
            });
        } else {
            dockerClient.pull(spec.getImage().getValue(), message -> {
            });
        }
    }

    public enum ImagePullPolicy {
        Never,
        Always,
        IfNotPresent
    }

}
