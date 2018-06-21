/**
 * ContainerProxy
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;

@Service
public class HeartbeatService {

	private static final String PROP_ENABLED = "proxy.heartbeat-enabled";
	private static final String PROP_RATE = "proxy.heartbeat-rate";
	private static final String PROP_TIMEOUT = "proxy.heartbeat-timeout";
	
	private Logger log = LogManager.getLogger(HeartbeatService.class);
	
	private Map<String, Long> proxyHeartbeats = Collections.synchronizedMap(new HashMap<>());
	
	@Inject
	private ProxyService proxyService;
	
	@Inject
	private Environment environment;
	
	@PostConstruct
	public void init() {
		boolean enabled = Boolean.valueOf(environment.getProperty(PROP_ENABLED, "false"));
		
		if (environment.getProperty(PROP_ENABLED) == null) {
			enabled = environment.getProperty(PROP_RATE) != null || environment.getProperty(PROP_TIMEOUT) != null;
		}
		
		if (enabled) {
			Thread cleanupThread = new Thread(new InactiveProxyKiller(), InactiveProxyKiller.class.getSimpleName());
			cleanupThread.setDaemon(true);
			cleanupThread.start();
		}
	}
	
	public long getHeartbeatRate() {
		return Long.parseLong(environment.getProperty(PROP_RATE, "10000"));
	}
	
	public long getHeartbeatTimeout() {
		return Long.parseLong(environment.getProperty(PROP_TIMEOUT, "60000"));
	}
	
	public void heartbeatReceived(String proxyId) {
		Proxy proxy = proxyService.getProxy(proxyId);
		if (proxy != null) proxyHeartbeats.put(proxyId, System.currentTimeMillis());
	}

	private class InactiveProxyKiller implements Runnable {
		@Override
		public void run() {
			long cleanupInterval = 2 * getHeartbeatRate();
			long heartbeatTimeout = getHeartbeatTimeout();

			while (true) {
				try {
					long currentTimestamp = System.currentTimeMillis();
					for (Proxy proxy: proxyService.getProxies(null, true)) {
						if (proxy.getStatus() != ProxyStatus.Up) continue;
						
						Long lastHeartbeat = proxyHeartbeats.get(proxy.getId());
						if (lastHeartbeat == null) lastHeartbeat = proxy.getStartupTimestamp();
						long proxySilence = currentTimestamp - lastHeartbeat;
						if (proxySilence > heartbeatTimeout) {
							log.info(String.format("Releasing inactive proxy [user: %s] [spec: %s] [id: %s] [silence: %dms]", proxy.getUserId(), proxy.getSpec().getId(), proxy.getId(), proxySilence));
							proxyHeartbeats.remove(proxy.getId());
							proxyService.stopProxy(proxy, true, true);
						}
					}
				} catch (Throwable t) {
					log.error("Error in " + this.getClass().getSimpleName(), t);
				}
				try {
					Thread.sleep(cleanupInterval);
				} catch (InterruptedException e) {}
			}
		}
	}
}
