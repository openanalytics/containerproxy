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
package eu.openanalytics.containerproxy.backend.docker;

import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.util.PortAllocator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;


public abstract class AbstractDockerBackend extends AbstractContainerBackend {

	private static final String PROPERTY_PREFIX = "proxy.docker.";

	protected static final String PROPERTY_APP_PORT = "port";
	protected static final String PROPERTY_PORT_RANGE_START = "port-range-start";
	protected static final String PROPERTY_PORT_RANGE_MAX = "port-range-max";

	protected static final String DEFAULT_TARGET_URL = DEFAULT_TARGET_PROTOCOL + "://localhost";

	protected PortAllocator portAllocator;
	protected DockerClient dockerClient;

	@Override
	public void initialize() throws ContainerProxyException {
		super.initialize();

		int startPort = Integer.valueOf(getProperty(PROPERTY_PORT_RANGE_START, "20000"));
		int maxPort = Integer.valueOf(getProperty(PROPERTY_PORT_RANGE_MAX, "-1"));
		portAllocator = new PortAllocator(startPort, maxPort);

		DefaultDockerClient.Builder builder = null;
		try {
			builder = DefaultDockerClient.fromEnv();
		} catch (DockerCertificateException e) {
			throw new ContainerProxyException("Failed to initialize docker client", e);
		}

		String confCertPath = getProperty(PROPERTY_CERT_PATH);
		if (confCertPath != null) {
			try {
				builder.dockerCertificates(DockerCertificates.builder().dockerCertPath(Paths.get(confCertPath)).build().orNull());
			} catch (DockerCertificateException e) {
				throw new ContainerProxyException("Failed to initialize docker client using certificates from " + confCertPath, e);
			}
		}

		String confUrl = getProperty(PROPERTY_URL);
		if (confUrl != null) builder.uri(confUrl);

		dockerClient = builder.build();
	}

	@Override
	public BiConsumer<OutputStream, OutputStream> getOutputAttacher(Proxy proxy) {
		Container c = getPrimaryContainer(proxy);
		if (c == null) return null;

		return (stdOut, stdErr) -> {
			try {
				LogStream logStream = dockerClient.logs(c.getId(), LogsParam.follow(), LogsParam.stdout(), LogsParam.stderr());
				logStream.attach(stdOut, stdErr);
			} catch (IOException | InterruptedException | DockerException e) {
				log.error("Error while attaching to container output", e);
			}
		};
	}

	@Override
	protected String getPropertyPrefix() {
		return PROPERTY_PREFIX;
	}

	protected Container getPrimaryContainer(Proxy proxy) {
		return proxy.getContainers().isEmpty() ? null : proxy.getContainers().get(0);
	}

	abstract protected URI calculateTarget(Container container, int containerPort, int hostPort) throws Exception;

	public void setupPortMappingExistingProxy(Proxy proxy, Container container, Map<Integer, Integer> portBindings) throws Exception {
		for (String mappingKey : container.getSpec().getPortMapping().keySet()) {
			int containerPort = container.getSpec().getPortMapping().get(mappingKey);

			int hostPort = -1; // in case of internal networking
			if (portBindings.containsKey(containerPort) && portBindings.get(containerPort) != 0) {
				// in case of non internal networking
				hostPort = portBindings.get(containerPort);
			}

			String mapping = mappingStrategy.createMapping(mappingKey, container, proxy);
			URI target = calculateTarget(container, containerPort, hostPort);
			proxy.getTargets().put(mapping, target);
		}
	}

	protected List<String> convertEnv(Map<String, String> env) {
		List<String> res = new ArrayList<>();

		for (Map.Entry<String, String> envVar : env.entrySet()) {
			res.add(String.format("%s=%s", envVar.getKey(), envVar.getValue()));
		}

		return res;
	}


	protected Map<RuntimeValueKey<?>, RuntimeValue> parseLabelsAsRuntimeValues(String containerId, ImmutableMap<String, String> labels) {
		if (labels == null) {
			return null;
		}

		Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = new HashMap<>();

		for (RuntimeValueKey<?> key : RuntimeValueKeyRegistry.getRuntimeValueKeys()) {
			if (key.getIncludeAsLabel() || key.getIncludeAsAnnotation()) {
				String value = labels.get(key.getKeyAsLabel());
				if (value != null) {
					runtimeValues.put(key, new RuntimeValue(key, value));
				} else if (key.isRequired()) {
					// value is null but is required
					log.warn("Ignoring container {} because no label named {} is found", containerId, key.getKeyAsLabel());
					return null;
				}
			}
		}

		return runtimeValues;
	}

}
