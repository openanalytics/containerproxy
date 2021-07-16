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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.service.ProxyService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service which 1) keeps track of active proxies by listening for heartbeats (created by {@link HeartbeatService})
 * and 2) kills proxies which where inactive for too long.
 */
@Service
public class ActiveProxiesService implements IHeartbeatProcessor {

    public static final String PROP_ENABLED = "proxy.heartbeat-enabled";
    public static final String PROP_RATE = "proxy.heartbeat-rate";
    public static final Long DEFAULT_RATE = 10000L;
    public static final String PROP_TIMEOUT = "proxy.heartbeat-timeout";
    public static final Long DEFAULT_TIMEOUT = 60000L;

    private final Logger log = LogManager.getLogger(HeartbeatService.class);

    private final Map<String, Long> proxyHeartbeats = Collections.synchronizedMap(new HashMap<>());

    private long cleanupInterval;
    private long heartbeatTimeout;

    @Inject
    private Environment environment;

    @Inject
    private ProxyService proxyService;

    @PostConstruct
    public void init() {
        Boolean enabled = environment.getProperty(PROP_ENABLED, Boolean.class);

        if (enabled == null) {
            enabled = environment.getProperty(PROP_RATE) != null || environment.getProperty(PROP_TIMEOUT) != null;
        }

        cleanupInterval = 2 * environment.getProperty(PROP_RATE, Long.class, DEFAULT_RATE);
        heartbeatTimeout = environment.getProperty(PROP_TIMEOUT, Long.class, DEFAULT_TIMEOUT);

        if (enabled) {
            Thread cleanupThread = new Thread(new InactiveProxyKiller(), InactiveProxyKiller.class.getSimpleName());
            cleanupThread.setDaemon(true);
            cleanupThread.start();
            log.debug("Releasing of inactive proxies enabled.");
        } else {
            log.debug("Releasing of inactive proxies disabled.");
        }
    }

    @Override
    public void heartbeatReceived(@Nonnull HeartbeatService.HeartbeatSource heartbeatSource, @Nonnull String proxyId, @Nullable String sessionId) {
        if (proxyService.getProxy(proxyId) != null) {
            if (log.isDebugEnabled()) log.debug("Heartbeat received for proxy " + proxyId);
            proxyHeartbeats.put(proxyId, System.currentTimeMillis());
        }
    }

    public Long getLastHeartBeat(String proxyId) {
        return proxyHeartbeats.get(proxyId);
    }

    private class InactiveProxyKiller implements Runnable {
        @Override
        public void run() {
            // TODO this could be replaced with a java Timer
            while (true) {
                try {
                    long currentTimestamp = System.currentTimeMillis();
                    for (Proxy proxy : proxyService.getProxies(null, true)) {
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
                } catch (InterruptedException e) {
                }
            }
        }
    }
}
