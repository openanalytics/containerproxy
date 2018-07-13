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
package eu.openanalytics.containerproxy.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import eu.openanalytics.containerproxy.service.HeartbeatService;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpServerConnection;

//TODO Do all browsers support websocket ping-pong?
public class HeartbeatConnector {

	private static final byte[] WEBSOCKET_PING = { (byte) 0b10001001, (byte) 0b00000000 };
	private static final byte WEBSOCKET_PONG = (byte) 0b10001010;
		
	private static ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(3);
	
	private HeartbeatService heartbeatService;
	private int heartbeatRate;
	private String proxyId;
	
	public HeartbeatConnector(HeartbeatService heartbeatService, String proxyId) {
		this.heartbeatService = heartbeatService;
		this.proxyId = proxyId;
		this.heartbeatRate = 2000;
	}
	
	public static void attach(HttpServerExchange exchange, String proxyId, HeartbeatService heartbeatService) {
		if (exchange.isUpgrade()) {
			// For websockets, attach an activity listener to the underlying TCP channel.
			HeartbeatConnector connector = new HeartbeatConnector(heartbeatService, proxyId);
			// Delay the wrapping, because Undertow will make changes to the channel while the upgrade is being performed.
			HttpServerConnection httpConn = (HttpServerConnection) exchange.getConnection();
			heartbeatExecutor.schedule(() -> connector.wrapChannels(httpConn.getChannel()), 3000, TimeUnit.MILLISECONDS);
		} else {
			// For regular HTTP requests, just trigger one heartbeat.
			heartbeatService.heartbeatReceived(proxyId);
		}
	}
	
	private void wrapChannels(StreamConnection streamConn) {
		if (!streamConn.isOpen()) return;
		
		ConduitStreamSinkChannel sinkChannel = streamConn.getSinkChannel();
		DelegatingStreamSinkConduit conduitWrapper = new DelegatingStreamSinkConduit(sinkChannel.getConduit(), null);
		sinkChannel.setConduit(conduitWrapper);
		
		ConduitStreamSourceChannel sourceChannel = streamConn.getSourceChannel();
		DelegatingStreamSourceConduit srcConduitWrapper = new DelegatingStreamSourceConduit(sourceChannel.getConduit(), data -> checkHeartbeatPong(data));
		sourceChannel.setConduit(srcConduitWrapper);
		
		heartbeatExecutor.schedule(() -> runHeartbeat(streamConn), heartbeatRate, TimeUnit.MILLISECONDS);
	}
	
	private void runHeartbeat(StreamConnection streamConn) {
		if (!streamConn.isOpen()) return;
		
		try {
			streamConn.getSinkChannel().write(ByteBuffer.wrap(WEBSOCKET_PING));
			streamConn.getSinkChannel().flush();
		} catch (IOException e) {
			// Ignore failure, keep trying as long as the stream connection is valid.
		}
		
		heartbeatExecutor.schedule(() -> runHeartbeat(streamConn), heartbeatRate, TimeUnit.MILLISECONDS);
	}

	private void checkHeartbeatPong(byte[] response) {
		if (response.length > 0 && response[0] == WEBSOCKET_PONG) {
			heartbeatService.heartbeatReceived(proxyId);
		}
	}
}
