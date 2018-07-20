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
package eu.openanalytics.containerproxy.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

@Service
public class LogService {

	private String containerLogPath;
	private ExecutorService executor;
	
	private Logger log = LogManager.getLogger(LogService.class);
	
	@Inject
	ProxyService proxyService;
	
	@Inject
	Environment environment;
	
	@PostConstruct
	public void init() {
		containerLogPath = environment.getProperty("proxy.container-log-path");
		if (containerLogPath != null && !containerLogPath.isEmpty()) {
			try {
				Files.createDirectories(Paths.get(containerLogPath));
				executor = Executors.newCachedThreadPool();
				log.info("Container logging enabled. Log files will be saved to " + containerLogPath);
			} catch (IOException e) {
				log.error("Failed to initialize container logging directory at " + containerLogPath, e);
				containerLogPath = null;
			}
		}
	}
	
	@PreDestroy
	public void shutdown() {
		if (executor != null) executor.shutdown();
	}

	public boolean isLoggingEnabled() {
		return containerLogPath != null && executor != null;
	}
	
	public void attachToOutput(Proxy proxy, BiConsumer<OutputStream, OutputStream> outputAttacher) {
		if (!isLoggingEnabled()) return;
		
		executor.submit(() -> {
			try {
				Path[] paths = getLogFilePaths(proxy);
				OutputStream stdOut = new FileOutputStream(paths[0].toFile());
				OutputStream stdErr = new FileOutputStream(paths[1].toFile());
				// Note that this call will block until the container is stopped.
				outputAttacher.accept(stdOut, stdErr);
			} catch (Exception e) {
				log.error("Failed to attach logging of proxy " + proxy.getId(), e);
			}
		});
	}
	
	private Path[] getLogFilePaths(Proxy proxy) {
		String timestamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
		return new Path[] {
			Paths.get(containerLogPath, String.format("%s_%s_%s_stdout.log", proxy.getSpec().getId(), proxy.getId(), timestamp)),
			Paths.get(containerLogPath, String.format("%s_%s_%s_stderr.log", proxy.getSpec().getId(), proxy.getId(), timestamp))
		};
	}
}
