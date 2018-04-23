/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.Container;
import eu.openanalytics.containerproxy.model.Proxy;
import eu.openanalytics.containerproxy.util.PortAllocator;


public abstract class AbstractDockerBackend extends AbstractContainerBackend {
	
	private static final String PROPERTY_PREFIX = "proxy.docker.";

	protected static final String PROPERTY_URL = "url";
	protected static final String PROPERTY_CONTAINER_PROTOCOL = "container-protocol";
	protected static final String PROPERTY_APP_PORT = "port";
	protected static final String PROPERTY_CERT_PATH = "cert-path";
	protected static final String PROPERTY_PORT_RANGE_START = "port-range-start";
	protected static final String PROPERTY_PORT_RANGE_MAX = "port-range-max";
	
	protected static final String DEFAULT_TARGET_PROTOCOL = "http";
	protected static final String DEFAULT_TARGET_URL = DEFAULT_TARGET_PROTOCOL + "://localhost";
	
	protected static final String ENV_VAR_SP_USER_NAME = "SHINYPROXY_USERNAME";
	protected static final String ENV_VAR_SP_USER_GROUPS = "SHINYPROXY_USERGROUPS";
	
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
	public BiConsumer<File, File> getOutputAttacher(Proxy proxy) {
		Container c = getPrimaryContainer(proxy);
		if (c == null) return null;
		
		return (stdOut, stdErr) -> {
			try {
				LogStream logStream = dockerClient.logs(c.getId(), LogsParam.follow(), LogsParam.stdout(), LogsParam.stderr());
				logStream.attach(new FileOutputStream(stdOut), new FileOutputStream(stdErr));
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
	
	protected List<String> buildEnv(Proxy proxy) throws IOException {
		//TODO
		List<String> env = new ArrayList<>();
		env.add(String.format("%s=%s", ENV_VAR_SP_USER_NAME, proxy.getUserId()));
		
//		String[] groups = userService.getGroups(userService.getCurrentAuth());
		String[] groups = new String[0];
		env.add(String.format("%s=%s", ENV_VAR_SP_USER_GROUPS, Arrays.stream(groups).collect(Collectors.joining(","))));
		
//		String envFile = app.getDockerEnvFile();
//		if (envFile != null && Files.isRegularFile(Paths.get(envFile))) {
//			Properties envProps = new Properties();
//			envProps.load(new FileInputStream(envFile));
//			for (Object key: envProps.keySet()) {
//				env.add(String.format("%s=%s", key, envProps.get(key)));
//			}
//		}
//
//		for (Map.Entry<String, String> entry : app.getDockerEnv().entrySet()) {
//			env.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
//		}
		
		return env;
	}
}
