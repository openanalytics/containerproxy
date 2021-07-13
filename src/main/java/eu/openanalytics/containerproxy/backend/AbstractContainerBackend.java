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
package eu.openanalytics.containerproxy.backend;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTargetMappingStrategy;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CreatedTimestampKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxiedAppKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxyIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxySpecIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RealmIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserGroupsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.service.AppRecoveryService;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.spec.expression.ExpressionAwareContainerSpec;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.util.SuccessOrFailure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
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

	@Inject
	protected IdentifierService identifierService;

	@Inject
	@Lazy
	// Note: lazy to prevent cyclic dependencies
	protected AppRecoveryService appRecoveryService;

	@Override
	public void initialize() throws ContainerProxyException {
		// If this application runs as a container itself, things like port publishing can be omitted.
		useInternalNetwork = Boolean.valueOf(getProperty(PROPERTY_INTERNAL_NETWORKING, "false"));
		privileged = Boolean.valueOf(getProperty(PROPERTY_PRIVILEGED, "false"));
	}

	@Override
	public SuccessOrFailure<Proxy> startProxy(Proxy proxy) throws ContainerProxyException {
		proxy.setId(UUID.randomUUID().toString());
		proxy.setStatus(ProxyStatus.Starting);
		proxy.setCreatedTimestamp(System.currentTimeMillis());
		setRuntimeValues(proxy);

		try {
			doStartProxy(proxy);

			if (proxy.getStatus().equals(ProxyStatus.Stopped) || proxy.getStatus().equals(ProxyStatus.Stopping)) {
				log.info(String.format("Pending proxy cleaned up [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpec().getId(), proxy.getId()));
				stopProxy(proxy);
				return SuccessOrFailure.createSuccess(proxy);
			}

			if (!testStrategy.testProxy(proxy)) {
				stopProxy(proxy);
				return SuccessOrFailure.createFailure(proxy, "Container did not respond in time");
			}

			proxy.setStartupTimestamp(System.currentTimeMillis());
			proxy.setStatus(ProxyStatus.Up);

			return SuccessOrFailure.createSuccess(proxy);
		} catch (Throwable t) {
			stopProxy(proxy);
			return SuccessOrFailure.createFailure(proxy, "Container failed to start", t);
		}

	}

	protected void doStartProxy(Proxy proxy) throws Exception {
		for (ContainerSpec spec: proxy.getSpec().getContainerSpecs()) {
			if (authBackend != null) authBackend.customizeContainer(spec);

			ExpressionAwareContainerSpec eSpec = new ExpressionAwareContainerSpec(spec,
					proxy,
					expressionResolver,
					userService.getCurrentAuth()
					);

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

	protected Map<String, String> buildEnv(ContainerSpec containerSpec, Proxy proxy) throws IOException {
        Map<String, String> env = new HashMap<>();

        for (RuntimeValue runtimeValue : proxy.getRuntimeValues().values()) {
			if (runtimeValue.getKey().getIncludeAsEnvironmentVariable()) {
				env.put(runtimeValue.getKey().getKeyAsEnvVar(), runtimeValue.getValue());
			}

			String envFile = containerSpec.getEnvFile();
			if (envFile != null && Files.isRegularFile(Paths.get(envFile))) {
				Properties envProps = new Properties();
				envProps.load(new FileInputStream(envFile));
				for (Object key : envProps.keySet()) {
					env.put(key.toString(), envProps.get(key).toString());
				}
			}

			if (containerSpec.getEnv() != null) {
				for (Map.Entry<String, String> entry : containerSpec.getEnv().entrySet()) {
					env.put(entry.getKey(), entry.getValue());
				}
			}

			// Allow the authentication backend to add values to the environment, if needed.
			if (authBackend != null) authBackend.customizeContainerEnv(env);
		}

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
		if (targetPath == null) {
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

	private void setRuntimeValues(Proxy proxy) {
		proxy.addRuntimeValue(new RuntimeValue(ProxiedAppKey.inst, "true"));
		proxy.addRuntimeValue(new RuntimeValue(ProxyIdKey.inst, proxy.getId()));
		proxy.addRuntimeValue(new RuntimeValue(InstanceIdKey.inst, identifierService.instanceId));
		proxy.addRuntimeValue(new RuntimeValue(ProxySpecIdKey.inst, proxy.getSpec().getId()));

		if (identifierService.realmId != null) {
			proxy.addRuntimeValue(new RuntimeValue(RealmIdKey.inst, identifierService.realmId));
		}

		proxy.addRuntimeValue(new RuntimeValue(UserIdKey.inst, proxy.getUserId()));
		String[] groups = userService.getGroups(userService.getCurrentAuth());
		proxy.addRuntimeValue(new RuntimeValue(UserGroupsKey.inst, String.join(",", groups)));
		proxy.addRuntimeValue(new RuntimeValue(CreatedTimestampKey.inst, Long.toString(proxy.getCreatedTimestamp())));
	}

}
