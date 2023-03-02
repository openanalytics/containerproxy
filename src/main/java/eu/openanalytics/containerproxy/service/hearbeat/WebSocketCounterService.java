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
package eu.openanalytics.containerproxy.service.hearbeat;

import eu.openanalytics.containerproxy.util.ProxyHashMap;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketCounterService implements IHeartbeatProcessor {

    public static final String PROP_RATE = "proxy.heartbeat-rate";
    public static final Long DEFAULT_RATE = 10000L;

    @Inject
    protected Environment environment;

    private final ConcurrentHashMap<String, Long> wsHeartbeats = ProxyHashMap.create();

    private long cleanupInterval;

    @PostConstruct
    public void init() {
        cleanupInterval = 5 * environment.getProperty(PROP_RATE, Long.class, DEFAULT_RATE);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateCount();
            }
        }, cleanupInterval, cleanupInterval);
    }


    @Override
    public void heartbeatReceived(@Nonnull HeartbeatService.HeartbeatSource heartbeatSource, @Nonnull String proxyId, @Nullable String sessionId) {
        if (heartbeatSource != HeartbeatService.HeartbeatSource.WEBSOCKET_PONG) {
            return;
        }

        wsHeartbeats.put(proxyId, System.currentTimeMillis());
    }

    private synchronized void updateCount() {
        long notBefore = System.currentTimeMillis() - cleanupInterval;
        wsHeartbeats.entrySet().removeIf(entry -> entry.getValue() < notBefore);
    }

    public synchronized int getCount() {
        return wsHeartbeats.size();
    }

}
