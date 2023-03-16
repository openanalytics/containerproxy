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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.HeartbeatTimeoutKey;
import eu.openanalytics.containerproxy.model.store.IHeartbeatStore;
import eu.openanalytics.containerproxy.service.IProxyReleaseStrategy;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import eu.openanalytics.containerproxy.service.leader.ILeaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Service which 1) keeps track of active proxies by listening for heartbeats (created by {@link HeartbeatService})
 * and 2) kills proxies which where inactive for too long.
 */
public class ActiveProxiesService implements IHeartbeatProcessor {

    public static final String PROP_RATE = "proxy.heartbeat-rate";
    public static final Long DEFAULT_RATE = 10000L;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final StructuredLogger slog = new StructuredLogger(log);

    @Inject
    protected IHeartbeatStore heartbeatStore;

    @Inject
    protected Environment environment;

    @Inject
    protected ProxyService proxyService;

    @Inject
    private IProxyReleaseStrategy releaseStrategy;

    @Inject
    private ILeaderService leaderService;

    @PostConstruct
    public void init() {
        long cleanupInterval = 2 * environment.getProperty(PROP_RATE, Long.class, DEFAULT_RATE);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                performCleanup();
            }
        }, cleanupInterval, cleanupInterval);
    }

    @Override
    public void heartbeatReceived(@Nonnull HeartbeatService.HeartbeatSource heartbeatSource, @Nonnull String proxyId, @Nullable String sessionId) {
        if (proxyService.getProxy(proxyId) != null) {
            if (log.isDebugEnabled()) log.debug("Heartbeat received for proxy " + proxyId);
            heartbeatStore.update(proxyId, System.currentTimeMillis());
        }
    }

    public Long getLastHeartBeat(String proxyId) {
        return heartbeatStore.get(proxyId);
    }

    public void setLastHeartBeat(String proxyId, Long time) {
        heartbeatStore.update(proxyId, time);
    }

    private void performCleanup() {
        if (!leaderService.isLeader()) {
            log.debug("Skipping checking heartbeats because we are not the leader");
            return;
        }
        try {
            long currentTimestamp = System.currentTimeMillis();
            for (Proxy proxy : proxyService.getProxies(null, true)) {
                checkAndReleaseProxy(currentTimestamp, proxy);
            }
        } catch (Throwable t) {
            log.error("Error in " + this.getClass().getSimpleName(), t);
        }
    }

    private void checkAndReleaseProxy(long currentTimestamp, Proxy proxy) {
        if (proxy.getStatus() != ProxyStatus.Up) {
            return;
        }

        Long heartbeatTimeout = proxy.getRuntimeObject(HeartbeatTimeoutKey.inst);

        if (heartbeatTimeout <= 0) {
            // heartbeats disabled for this app (or globally)
            return;
        }

        Long lastHeartbeat = heartbeatStore.get(proxy.getId());
        if (lastHeartbeat == null) {
            lastHeartbeat = proxy.getStartupTimestamp();
        }

        long proxySilence = currentTimestamp - lastHeartbeat;
        if (proxySilence > heartbeatTimeout) {
            slog.info(proxy, String.format("Releasing inactive proxy [silence: %dms]", proxySilence));
            releaseStrategy.releaseProxy(proxy);
        }
    }

}
