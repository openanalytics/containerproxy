/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTargetMappingStrategy;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.spec.expression.ExpressionAwareContainerSpec;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;

public abstract class AbstractContainerBackend implements IContainerBackend {

	protected static final String PROPERTY_INTERNAL_NETWORKING = "internal-networking";
	protected static final String PROPERTY_URL = "url";
	protected static final String PROPERTY_CERT_PATH = "cert-path";
	protected static final String PROPERTY_CONTAINER_PROTOCOL = "container-protocol";
	protected static final String PROPERTY_PRIVILEGED = "privileged";
	
	protected static final String DEFAULT_TARGET_PROTOCOL = "http";
	
	//TODO rename vars?
	protected static final String ENV_VAR_USER_NAME = "SHINYPROXY_USERNAME";
	protected static final String ENV_VAR_USER_GROUPS = "SHINYPROXY_USERGROUPS";
	
	protected final Logger log = LogManager.getLogger(getClass());
	
	private boolean useInternalNetwork;
	private boolean privileged;
	
	@Inject
	protected IProxyTargetMappingStrategy mappingStrategy;

	@Inject
	protected IProxyTestStrategy testStrategy;
	
	@Inject
	protected UserService userService;
	
	@Inject
	protected Environment environment;
	
	@Inject
	protected SpecExpressionResolver expressionResolver;
	
	@Inject
	@Lazy
	// Note: lazy needed to work around early initialization conflict 
	protected IAuthenticationBackend authBackend;
	
	@Override
	public void initialize() throws ContainerProxyException {
		// If this application runs as a container itself, things like port publishing can be omitted.
		useInternalNetwork = Boolean.valueOf(getProperty(PROPERTY_INTERNAL_NETWORKING, "false"));
		privileged = Boolean.valueOf(getProperty(PROPERTY_PRIVILEGED, "false"));
	}
	
	@Override
	public void startProxy(Proxy proxy) throws ContainerProxyException {
		proxy.setId(UUID.randomUUID().toString());
		proxy.setStatus(ProxyStatus.Starting);
		
		try {
			doStartProxy(proxy);
		} catch (Throwable t) {
			stopProxy(proxy);
			throw new ContainerProxyException("Failed to start container", t);
		}
		
		if (!testStrategy.testProxy(proxy)) {
			stopProxy(proxy);
			throw new ContainerProxyException("Container did not respond in time");
		}
		
		proxy.setStartupTimestamp(System.currentTimeMillis());
		proxy.setStatus(ProxyStatus.Up);
	}
	
	protected void doStartProxy(Proxy proxy) throws Exception {
		for (ContainerSpec spec: proxy.getSpec().getContainerSpecs()) {
			if (authBackend != null) authBackend.customizeContainer(spec);
			ExpressionAwareContainerSpec eSpec = new ExpressionAwareContainerSpec(spec, proxy, expressionResolver);
			Container c = startContainer(eSpec, proxy);
			c.setSpec(spec);
			proxy.getContainers().add(c);
		}
	}
	
	protected abstract Container startContainer(ContainerSpec spec, Proxy proxy) throws Exception;
	
	@Override
	public void stopProxy(Proxy proxy) throws ContainerProxyException {
		try {
			proxy.setStatus(ProxyStatus.Stopping);
			doStopProxy(proxy);
			proxy.setStatus(ProxyStatus.Stopped);
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
	
	protected List<String> buildEnv(ContainerSpec containerSpec, Proxy proxy) throws IOException {
		List<String> env = new ArrayList<>();
		env.add(String.format("%s=%s", ENV_VAR_USER_NAME, proxy.getUserId()));
		
		String[] groups = userService.getGroups(userService.getCurrentAuth());
		env.add(String.format("%s=%s", ENV_VAR_USER_GROUPS, Arrays.stream(groups).collect(Collectors.joining(","))));
		
		String envFile = containerSpec.getEnvFile();
		if (envFile != null && Files.isRegularFile(Paths.get(envFile))) {
			Properties envProps = new Properties();
			envProps.load(new FileInputStream(envFile));
			for (Object key: envProps.keySet()) {
				env.add(String.format("%s=%s", key, envProps.get(key)));
			}
		}

		if (containerSpec.getEnv() != null) {
			for (Map.Entry<String, String> entry : containerSpec.getEnv().entrySet()) {
				env.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
			}
		}
		
		// Allow the authentication backend to add values to the environment, if needed.
		if (authBackend != null) authBackend.customizeContainerEnv(env);
		
		return env;
	}
	
	protected boolean isUseInternalNetwork() {
		return useInternalNetwork;
	}
	
	protected boolean isPrivileged() {
		return privileged;
	}
}
