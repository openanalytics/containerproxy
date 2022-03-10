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
package eu.openanalytics.containerproxy.service.hearbeat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import eu.openanalytics.containerproxy.service.session.ISessionService;
import eu.openanalytics.containerproxy.util.ChannelActiveListener;
import eu.openanalytics.containerproxy.util.DelegatingStreamSinkConduit;
import eu.openanalytics.containerproxy.util.DelegatingStreamSourceConduit;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpServerConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatService {


    private static final byte[] WEBSOCKET_PING = {(byte) 0b10001001, (byte) 0b00000000};
    private static final byte WEBSOCKET_PONG = (byte) 0b10001010;

    private final Logger log = LogManager.getLogger(HeartbeatService.class);

    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(3);

    public enum HeartbeatSource {
        /**
         * Heartbeat send because of an incoming HTTP request.
         */
        HTTP_REQUEST,
        /**
         * Heartbeat send because of a response (pong) to a websocket ping.
         */
        WEBSOCKET_PONG,
        /**
         * Heartbeat send because of some internal event in ContainerProxy. This heartbeat is not directly caused
         * by action of the user.
         */
        INTERNAL,
        /**
         * Heartbeat send because of a fallback heartbeat request.
         */
        FALLBACK
    }

    @Inject
    private Environment environment;

    @Inject
    private ISessionService sessionService;

    @Inject
    @Lazy
    private HeartbeatService self;

    private final List<IHeartbeatProcessor> heartbeatProcessors;

    // keep track of the HeartbeatConnector for every SessionId so that the websocket connection can be closed
    // when the user logs out from that session. This is required for apps that keep running even if when the user signs out.
    private final ListMultimap<String, HeartbeatConnector> heartbeatConnectors = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());

    public HeartbeatService(List<IHeartbeatProcessor> heartbeatProcessors) {
        this.heartbeatProcessors = heartbeatProcessors;
    }

    public void attachHeartbeatChecker(HttpServerExchange exchange, String proxyId) {
        if (exchange.isUpgrade()) {
            // For websockets, attach a ping-pong listener to the underlying TCP channel.
            String sessionId = sessionService.extractSessionIdFromExchange(exchange);
            HttpServerConnection httpConn = (HttpServerConnection) exchange.getConnection();
            HeartbeatConnector connector = new HeartbeatConnector(proxyId, sessionId, httpConn.getChannel());
            // Delay the wrapping, because Undertow will make changes to the channel while the upgrade is being performed.
            heartbeatExecutor.schedule(() -> connector.wrapChannels(httpConn.getChannel()), 3000, TimeUnit.MILLISECONDS);
            heartbeatConnectors.put(sessionId, connector);
        } else {
            // For regular HTTP requests, just trigger one heartbeat.
            self.heartbeatReceived(HeartbeatSource.HTTP_REQUEST, proxyId, null);
        }
    }

    /**
     * Indicates that a heartbeat was received. This method is made async because this method will be called for every
     * HTTP request and every Websocket ping/pong. Both the {@link ActiveProxiesService} and {@link SessionReActivatorService}
     * can make requests to the Redis backend (either for updating the session or for updating the timestamp of active session).
     * By making this method async, we prevent that (proxied!) HTTP requests are blocked on calls to Redis.
     * This method uses the taskExecutor bean defined in {@link eu.openanalytics.containerproxy.ContainerProxyApplication}
     * in order to execute the tasks.
     */
    @Async
    public void heartbeatReceived(@Nonnull HeartbeatService.HeartbeatSource heartbeatSource, @Nonnull String proxyId, @Nullable String sessionId) {
        for (IHeartbeatProcessor heartbeatProcessor : heartbeatProcessors) {
            heartbeatProcessor.heartbeatReceived(heartbeatSource, proxyId, sessionId);
        }
        if (log.isDebugEnabled()) log.debug(String.format("Heartbeat received [proxyId: %s] [source: %s]", proxyId, heartbeatSource));
    }

    public long getHeartbeatRate() {
        return environment.getProperty(ActiveProxiesService.PROP_RATE, Long.class, ActiveProxiesService.DEFAULT_RATE);
    }

    @EventListener
    public void onSessionDestroyedEvent(HttpSessionDestroyedEvent event) {
        // stop every websocket connection started by the session
        heartbeatConnectors.get(event.getId()).forEach(HeartbeatConnector::closeConnection);
        // remove the session from the map
        heartbeatConnectors.removeAll(event.getId());
    }

    private class HeartbeatConnector {

        private final String proxyId;

        private final String sessionId;

        private StreamConnection streamConnection;

        private HeartbeatConnector(String proxyId, String sessionId, StreamConnection streamConnection) {
            this.proxyId = proxyId;
            this.sessionId = sessionId;
            this.streamConnection = streamConnection;
        }

        private void wrapChannels(StreamConnection streamConn) {
            if (!streamConn.isOpen()) return;
            this.streamConnection = streamConn; // save final streamConnection

            ConduitStreamSinkChannel sinkChannel = streamConn.getSinkChannel();
            ChannelActiveListener writeListener = new ChannelActiveListener();
            DelegatingStreamSinkConduit conduitWrapper = new DelegatingStreamSinkConduit(sinkChannel.getConduit(), writeListener);
            sinkChannel.setConduit(conduitWrapper);

            ConduitStreamSourceChannel sourceChannel = streamConn.getSourceChannel();
            DelegatingStreamSourceConduit srcConduitWrapper = new DelegatingStreamSourceConduit(sourceChannel.getConduit(), this::checkPong);
            sourceChannel.setConduit(srcConduitWrapper);

            heartbeatExecutor.schedule(() -> sendPing(writeListener, streamConn), getHeartbeatRate(), TimeUnit.MILLISECONDS);
        }

        private void sendPing(ChannelActiveListener writeListener, StreamConnection streamConn) {
            if (writeListener.isActive(getHeartbeatRate())) {
                // active means that data was written to the channel in the least heartbeat interval
                // therefore we don't send a ping now to not cause collisions

                // reschedule ping
                heartbeatExecutor.schedule(() -> sendPing(writeListener, streamConn), getHeartbeatRate(), TimeUnit.MILLISECONDS);
                // mark as we received a heartbeat
                self.heartbeatReceived(HeartbeatSource.WEBSOCKET_PONG, proxyId, sessionId);
                return;
            }
            if (!streamConn.isOpen()) return;

            try {
                ((DelegatingStreamSinkConduit) streamConn.getSinkChannel().getConduit()).writeWithoutNotifying(ByteBuffer.wrap(WEBSOCKET_PING));
                streamConn.getSinkChannel().flush();
            } catch (IOException e) {
                // Ignore failure, keep trying as long as the stream connection is valid.
            }

            heartbeatExecutor.schedule(() -> sendPing(writeListener, streamConn), getHeartbeatRate(), TimeUnit.MILLISECONDS);
        }

        private void checkPong(byte[] response) {
            if (response.length > 0 && response[0] == WEBSOCKET_PONG) {
                self.heartbeatReceived(HeartbeatSource.WEBSOCKET_PONG, proxyId, sessionId);
            }
        }

        /**
         * Closes the WebSocket connection associated with this connector.
         */
        public void closeConnection()  {
            try {
                if (streamConnection != null) {
                    streamConnection.close();
                }
            } catch (Throwable e) {
                // ignore error since we cannot do anything about it anyway
            }
        }

    }

}
