/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.event.ProxyPauseEvent;
import eu.openanalytics.containerproxy.event.ProxyResumeEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.log.ILogStorage;
import eu.openanalytics.containerproxy.log.LogPaths;
import eu.openanalytics.containerproxy.log.LogStreams;
import eu.openanalytics.containerproxy.log.NoopLogStorage;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import eu.openanalytics.containerproxy.service.leader.ILeaderService;
import eu.openanalytics.containerproxy.util.ProxyHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

@Service
public class LogService {

    private final Logger log = LogManager.getLogger(LogService.class);
    @Inject
    ILogStorage logStorage;
    @Inject
    ILeaderService iLeaderService;
    @Inject
    IProxyStore proxyStore;
    @Inject
    IContainerBackend backend;
    private ExecutorService executor;
    private boolean loggingEnabled;
    // do not use ProxyHashMap
    private ConcurrentHashMap<String, LogStreams> proxyStreams = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            logStorage.initialize();
            loggingEnabled = !(logStorage instanceof NoopLogStorage);
        } catch (IOException e) {
            log.error("Failed to initialize container log storage", e);
        }

        if (iLeaderService.isLeader()) {
            startService();
        }
    }

    @PreDestroy
    public void shutdown() {
        stopService();
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
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

    @EventListener
    public void onProxyStarted(ProxyStartEvent event) {
        if (!isLoggingEnabled() || !iLeaderService.isLeader()) return;
        Proxy proxy = proxyStore.getProxy(event.getProxyId());
        if (proxy == null) {
            return;
        }

        attachToOutput(proxy);
    }

    @EventListener
    public void onProxyResumed(ProxyResumeEvent event) {
        if (!isLoggingEnabled() || !iLeaderService.isLeader()) return;
        Proxy proxy = proxyStore.getProxy(event.getProxyId());
        if (proxy == null) {
            return;
        }

        attachToOutput(proxy);
    }

    @EventListener
    public void onProxyStopped(ProxyStopEvent event) {
        if (!isLoggingEnabled() || !iLeaderService.isLeader()) return;
        Proxy proxy = proxyStore.getProxy(event.getProxyId());
        if (proxy == null) {
            return;
        }

        detach(proxy);
    }

    @EventListener
    public void onProxyPaused(ProxyPauseEvent event) {
        if (!isLoggingEnabled() || !iLeaderService.isLeader()) return;
        Proxy proxy = proxyStore.getProxy(event.getProxyId());
        if (proxy == null) {
            return;
        }

        detach(proxy);
    }

    @EventListener(OnGrantedEvent.class)
    public void onLeaderGranted() {
        startService();
    }

    @EventListener(OnRevokedEvent.class)
    public void onLeaderRevoked() {
        stopService();
    }

    /**
     * synchronized to avoid duplicate starts
     */
    private synchronized void startService() {
        if (!isLoggingEnabled()) return;
        if (executor == null) {
            executor = Executors.newCachedThreadPool();
        }
        log.info("Container logging enabled. Log files will be saved to " + logStorage.getStorageLocation());
        // attach existing proxies
        for (Proxy proxy : proxyStore.getAllProxies()) {
            attachToOutput(proxy);
        }
    }

    /**
     * synchronized to avoid duplicate starts
     */
    private synchronized void stopService() {
        if (!isLoggingEnabled()) return;
        if (executor != null) {
            executor.shutdown();
        }
        executor = null;
        for (Map.Entry<String, LogStreams> streams : proxyStreams.entrySet()) {
            detach(streams.getKey(), streams.getValue());
        }
        proxyStreams = ProxyHashMap.create();
        logStorage.stopService();
    }

    public void attachToOutput(Proxy proxy) {
        if (!isLoggingEnabled() || !Objects.equals(proxy.getId(), proxy.getTargetId())) {
            return;
        }
        if (!iLeaderService.isLeader()) {
            log.warn("Cannot log proxy output: not the leader.");
            return;
        }
        BiConsumer<OutputStream, OutputStream> outputAttacher = backend.getOutputAttacher(proxy);
        if (outputAttacher == null) {
            log.warn("Cannot log proxy output: " + backend.getClass() + " does not support output attaching.");
            return;
        }

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
        if (!isLoggingEnabled() || !Objects.equals(proxy.getId(), proxy.getTargetId())) {
            return;
        }
        LogStreams streams = proxyStreams.get(proxy.getId());
        if (streams == null) {
            log.warn("Cannot detach container logging: streams not found");
            return;
        }
        detach(proxy.getId(), streams);
    }

    private void detach(String proxyId, LogStreams streams) {
        try {
            streams.getStdout().flush();
            streams.getStdout().close();
            streams.getStderr().flush();
            streams.getStderr().close();
        } catch (IOException e) {
            log.error("Failed to close container logging streams", e);
        }
        proxyStreams.remove(proxyId);
    }

}
