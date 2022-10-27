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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.log.ILogStorage;
import eu.openanalytics.containerproxy.log.LogPaths;
import eu.openanalytics.containerproxy.log.LogStreams;
import eu.openanalytics.containerproxy.log.NoopLogStorage;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

@Service
public class LogService {

	private ExecutorService executor;
	private boolean loggingEnabled;
	private final Logger log = LogManager.getLogger(LogService.class);
	
	private final ConcurrentHashMap<String, LogStreams> proxyStreams = new ConcurrentHashMap<>();

	@Inject
	ILogStorage logStorage;
	
	@PostConstruct
	public void init() {
		try {
			logStorage.initialize();
			loggingEnabled = !(logStorage instanceof NoopLogStorage);
		} catch (IOException e) {
			log.error("Failed to initialize container log storage", e);
		}
		
		if (isLoggingEnabled()) {
			executor = Executors.newCachedThreadPool();
			log.info("Container logging enabled. Log files will be saved to " + logStorage.getStorageLocation());
		}
	}
	
	@PreDestroy
	public void shutdown() {
		if (executor != null) executor.shutdown();
	}

	public boolean isLoggingEnabled() {
		return loggingEnabled;
	}
	
	public void attachToOutput(Proxy proxy, BiConsumer<OutputStream, OutputStream> outputAttacher) {
		if (!isLoggingEnabled()) return;
		
		executor.submit(() -> {
			try {
				LogStreams streams = logStorage.createOutputStreams(proxy);
				if (streams == null) {
					log.error("Failed to attach logging of proxy " + proxy.getId() + ": no output streams defined");
					return;
				}
				proxyStreams.put(proxy.getId(), streams);
				if (log.isDebugEnabled()) log.debug("Container logging started for proxy " + proxy.getId());
				// Note that this call will block until the container is stopped.
				outputAttacher.accept(streams.getStdout(), streams.getStderr());
			} catch (Exception e) {
				log.error("Failed to attach logging of proxy " + proxy.getId(), e);
			}
			if (log.isDebugEnabled()) log.debug("Container logging ended for proxy " + proxy.getId());
		});
	}
	
	public void detach(Proxy proxy) {
		if (!isLoggingEnabled()) return;

		LogStreams streams = proxyStreams.get(proxy.getId());
		if (streams == null) {
			log.warn("Cannot detach container logging: streams not found");
			return;
		}
		try {
			streams.getStdout().flush();
			streams.getStdout().close();
			streams.getStderr().flush();
			streams.getStderr().close();
		} catch (IOException e) {
			log.error("Failed to close container logging streams", e);
		}
	}
	
	public LogPaths getLogs(Proxy proxy) {
		if (!isLoggingEnabled()) return null;
		
		try {
			return logStorage.getLogs(proxy);
		} catch (IOException e) {
			log.error("Failed to locate logs for proxy " + proxy.getId(), e);
		}
		
		return null;
	}
	
}
