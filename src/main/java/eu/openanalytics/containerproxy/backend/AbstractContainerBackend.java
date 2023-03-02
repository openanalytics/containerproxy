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
package eu.openanalytics.containerproxy.backend;

import eu.openanalytics.containerproxy.ContainerFailedToStartException;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTargetMappingStrategy;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.PortMappings;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PortMappingsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.AppRecoveryService;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractContainerBackend implements IContainerBackend {

	protected static final String PROPERTY_INTERNAL_NETWORKING = "internal-networking";
	protected static final String PROPERTY_URL = "url";
	protected static final String PROPERTY_CERT_PATH = "cert-path";
	protected static final String PROPERTY_CONTAINER_PROTOCOL = "container-protocol";
	protected static final String PROPERTY_PRIVILEGED = "privileged";
	protected static final String DEFAULT_TARGET_PROTOCOL = "http";
	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected final StructuredLogger slog = new StructuredLogger(log);

	private boolean useInternalNetwork;
	private boolean privileged;

	@Inject
	protected IProxyTargetMappingStrategy mappingStrategy;

	@Inject
	protected Environment environment;

	@Inject
	@Lazy
	// Note: lazy needed to work around early initialization conflict
	protected IAuthenticationBackend authBackend;

	@Inject
	@Lazy
	// Note: lazy to prevent cyclic dependencies
	protected AppRecoveryService appRecoveryService;

	@Inject
	protected IdentifierService identifierService;

	@Override
	public void initialize() throws ContainerProxyException {
		// If this application runs as a container itself, things like port publishing can be omitted.
		useInternalNetwork = Boolean.valueOf(getProperty(PROPERTY_INTERNAL_NETWORKING, "false"));
		privileged = Boolean.valueOf(getProperty(PROPERTY_PRIVILEGED, "false"));
	}

	@Override
	public Proxy startProxy(Authentication user, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ProxyFailedToStartException {
		List<Container> resultContainers = new ArrayList<>();

		for (ContainerSpec spec: proxySpec.getContainerSpecs()) {
			try {
				Container container = proxy.getContainer(spec.getIndex());
				container = startContainer(user, container, spec, proxy, proxySpec, proxyStartupLogBuilder);
				resultContainers.add(container);
				if (container.getIndex() == 0) {
					proxyStartupLogBuilder.startingApplication();
				}
			} catch (ContainerFailedToStartException t) {
				resultContainers.add(t.getContainer());
				proxy = proxy.toBuilder().containers(resultContainers).build();
				throw new ProxyFailedToStartException(String.format("Container with index %s failed to start", spec.getIndex()), t, proxy);
			}
		}

		return proxy.toBuilder().containers(resultContainers).build();
	}

	protected abstract Container startContainer(Authentication user, Container Container, ContainerSpec spec, Proxy proxy, ProxySpec proxySpec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ContainerFailedToStartException;

	@Override
	public void stopProxy(Proxy proxy) throws ContainerProxyException {
		try {
			doStopProxy(proxy);
		} catch (Exception e) {
			throw new ContainerProxyException("Failed to stop container", e);
		}
	}

	protected abstract void doStopProxy(Proxy proxy) throws Exception;

	@Override
	public BiConsumer<OutputStream, OutputStream> getOutputAttacher(Proxy proxy) {
		// Default: do not support output attaching.
		return null;
	}

	protected String getProperty(String key) {
		return getProperty(key, null);
	}

	protected String getProperty(String key, String defaultValue) {
		return environment.getProperty(getPropertyPrefix() + key, defaultValue);
	}

	protected abstract String getPropertyPrefix();

	protected Long memoryToBytes(String memory) {
		if (memory == null || memory.isEmpty()) return null;
		Matcher matcher = Pattern.compile("(\\d+)([bkmg]?)").matcher(memory.toLowerCase());
		if (!matcher.matches()) throw new IllegalArgumentException("Invalid memory argument: " + memory);
		long mem = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2);
		switch (unit) {
		case "k":
			mem *= 1024;
			break;
		case "m":
			mem *= 1024*1024;
			break;
		case "g":
			mem *= 1024*1024*1024;
			break;
		default:
		}
		return mem;
	}

	protected Map<String, String> buildEnv(Authentication user, ContainerSpec containerSpec, Proxy proxy) throws IOException {
        Map<String, String> env = new HashMap<>();

        for (RuntimeValue runtimeValue : proxy.getRuntimeValues().values()) {
			if (runtimeValue.getKey().getIncludeAsEnvironmentVariable()) {
				env.put(runtimeValue.getKey().getKeyAsEnvVar(), runtimeValue.toString());
			}

			Path envFile = containerSpec.getEnvFile().mapOrNull(Paths::get);
			if (envFile != null && Files.isRegularFile(envFile)) {
				Properties envProps = new Properties();
				envProps.load(Files.newInputStream(envFile));
				for (Object key : envProps.keySet()) {
					env.put(key.toString(), envProps.get(key).toString());
				}
			}

			if (containerSpec.getEnv().isPresent()) {
				env.putAll(containerSpec.getEnv().getValue());
			}
		}

		// Allow the authentication backend to add values to the environment, if needed.
		if (authBackend != null) authBackend.customizeContainerEnv(user, env);

		return env;
	}

	protected boolean isUseInternalNetwork() {
		return useInternalNetwork;
	}

	protected boolean isPrivileged() {
		return privileged;
	}

	/**
	 * Computes the correct targetPath to use, to make the configuration of the targetPath easier.
	 *  - Removes any double slashes (can happen when using SpeL surrounded with static paths)
	 *  - Ensures the path does not end with a slash. The rest of the code assumes the targetPath does not end with a slash.
	 *  - Ensures the path starts with a slash (as it will be concatenated after the targetPort)
	 *  - Ensures the path is empty when not path is defined (or when a single / is defined)
	 */
	public static String computeTargetPath(String targetPath) {
		if (targetPath == null || targetPath.equals("")) {
			return "";
		}

		targetPath = targetPath.replaceAll("/+", "/"); // replace consecutive slashes

		if (!targetPath.startsWith("/")) {
			targetPath = "/" + targetPath;
		}

		if (targetPath.endsWith("/")) {
			// remove every ending /
			targetPath = targetPath.substring(0, targetPath.length() - 1);
		}

		return targetPath;
	}

	abstract protected URI calculateTarget(Container container, PortMappings.PortMappingEntry portMapping, Integer hostPort) throws Exception;

	public Container setupPortMappingExistingProxy(Proxy proxy, Container container, Map<Integer, Integer> portBindings) throws Exception {
		Container.ContainerBuilder containerBuilder = container.toBuilder();
		for (PortMappings.PortMappingEntry portMapping : container.getRuntimeObject(PortMappingsKey.inst).getPortMappings()) {

			Integer boundPort = null; // in case of internal networking
			if (!isUseInternalNetwork()) {
				// in case of non-internal networking
				boundPort = portBindings.get(portMapping.getPort());
			}

			String mapping = mappingStrategy.createMapping(portMapping.getName(), container, proxy);
			URI target = calculateTarget(container, portMapping, boundPort);
			containerBuilder.addTarget(mapping, target);
		}
		return containerBuilder.build();
	}

}
