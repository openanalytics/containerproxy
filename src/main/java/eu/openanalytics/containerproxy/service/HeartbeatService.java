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
package eu.openanalytics.containerproxy.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.util.DelegatingStreamSinkConduit;
import eu.openanalytics.containerproxy.util.DelegatingStreamSourceConduit;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpServerConnection;

@Service
public class HeartbeatService {

	private static final String PROP_ENABLED = "proxy.heartbeat-enabled";
	private static final String PROP_RATE = "proxy.heartbeat-rate";
	private static final String PROP_TIMEOUT = "proxy.heartbeat-timeout";
	
	private static final byte[] WEBSOCKET_PING = { (byte) 0b10001001, (byte) 0b00000000 };
	private static final byte WEBSOCKET_PONG = (byte) 0b10001010;

	private Logger log = LogManager.getLogger(HeartbeatService.class);
	
	private Map<String, Long> proxyHeartbeats = Collections.synchronizedMap(new HashMap<>());
		
	private ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(3);
	
	private volatile boolean enabled;
	
	@Inject
	private ProxyService proxyService;
	
	@Inject
	private Environment environment;
	
	@PostConstruct
	public void init() {
		enabled = Boolean.valueOf(environment.getProperty(PROP_ENABLED, "false"));
		
		if (environment.getProperty(PROP_ENABLED) == null) {
			enabled = environment.getProperty(PROP_RATE) != null || environment.getProperty(PROP_TIMEOUT) != null;
		}
		
		Thread cleanupThread = new Thread(new InactiveProxyKiller(), InactiveProxyKiller.class.getSimpleName());
		cleanupThread.setDaemon(true);
		cleanupThread.start();
	}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	public void attachHeartbeatChecker(HttpServerExchange exchange, String proxyId) {
		if (exchange.isUpgrade()) {
			// For websockets, attach a ping-pong listener to the underlying TCP channel.
			HeartbeatConnector connector = new HeartbeatConnector(proxyId);
			// Delay the wrapping, because Undertow will make changes to the channel while the upgrade is being performed.
			HttpServerConnection httpConn = (HttpServerConnection) exchange.getConnection();
			heartbeatExecutor.schedule(() -> connector.wrapChannels(httpConn.getChannel()), 3000, TimeUnit.MILLISECONDS);
		} else {
			// For regular HTTP requests, just trigger one heartbeat.
			heartbeatReceived(proxyId);
		}
	}
	
	private void heartbeatReceived(String proxyId) {
		Proxy proxy = proxyService.getProxy(proxyId);
		if (log.isDebugEnabled()) log.debug("Heartbeat received for proxy " + proxyId);
		if (proxy != null) proxyHeartbeats.put(proxyId, System.currentTimeMillis());
	}
	
	private long getHeartbeatRate() {
		return Long.parseLong(environment.getProperty(PROP_RATE, "10000"));
	}
	
	private long getHeartbeatTimeout() {
		return Long.parseLong(environment.getProperty(PROP_TIMEOUT, "60000"));
	}
	
	private class HeartbeatConnector {

		private String proxyId;
		
		public HeartbeatConnector(String proxyId) {
			this.proxyId = proxyId;
		}
		
		private void wrapChannels(StreamConnection streamConn) {
			if (!streamConn.isOpen()) return;
			
			ConduitStreamSinkChannel sinkChannel = streamConn.getSinkChannel();
			DelegatingStreamSinkConduit conduitWrapper = new DelegatingStreamSinkConduit(sinkChannel.getConduit(), null);
			sinkChannel.setConduit(conduitWrapper);
			
			ConduitStreamSourceChannel sourceChannel = streamConn.getSourceChannel();
			DelegatingStreamSourceConduit srcConduitWrapper = new DelegatingStreamSourceConduit(sourceChannel.getConduit(), data -> checkPong(data));
			sourceChannel.setConduit(srcConduitWrapper);
			
			heartbeatExecutor.schedule(() -> sendPing(streamConn), getHeartbeatRate(), TimeUnit.MILLISECONDS);
		}
		
		private void sendPing(StreamConnection streamConn) {
			if (!streamConn.isOpen()) return;
			
			try {
				streamConn.getSinkChannel().write(ByteBuffer.wrap(WEBSOCKET_PING));
				streamConn.getSinkChannel().flush();
			} catch (IOException e) {
				// Ignore failure, keep trying as long as the stream connection is valid.
			}
			
			heartbeatExecutor.schedule(() -> sendPing(streamConn), getHeartbeatRate(), TimeUnit.MILLISECONDS);
		}

		private void checkPong(byte[] response) {
			if (response.length > 0 && response[0] == WEBSOCKET_PONG) {
				heartbeatReceived(proxyId);
			}
		}
	}
	
	private class InactiveProxyKiller implements Runnable {
		@Override
		public void run() {
			long cleanupInterval = 2 * getHeartbeatRate();
			long heartbeatTimeout = getHeartbeatTimeout();

			while (true) {
				if (enabled) {
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
				}
				try {
					Thread.sleep(cleanupInterval);
				} catch (InterruptedException e) {}
			}
		}
	}
}
