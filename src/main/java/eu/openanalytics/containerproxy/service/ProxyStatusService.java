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

import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProxyStatusService {

    private final Map<String, ProxyStartupLog> startupLog = new ConcurrentHashMap<>();

    /**
     * Step 1: proxy has been created
     */
    public void proxyCreated(Proxy proxy) {
        ProxyStartupLog proxyStartupLog = new ProxyStartupLog();
        proxyStartupLog.getCreateProxy().stepStarted();
        startupLog.put(proxy.getId(), proxyStartupLog);
    }

    /**
     * Step 2: container has been created and is starting up
     */
    public void containerStarting(Proxy proxy, Container container) {
        startupLog.get(proxy.getId()).getStartContainer(container.getIndex()).stepStarted();
    }

    /**
     * Step 2.1: schedule container
     */
    public void containerScheduled(Proxy proxy, Container container, LocalDateTime scheduledTime) {
        startupLog.get(proxy.getId())
                .getScheduleContainer(container.getIndex())
                .stepSucceeded(
                    startupLog.get(proxy.getId()).getStartContainer(container.getIndex()).getStartTime(),
                    scheduledTime
                );
    }

    /**
     * Step 2.2: pull image
     */
    public void imagePulling(Proxy proxy, Container container) {
        startupLog.get(proxy.getId()).getPullImage(container.getIndex()).stepStarted();
    }

    public void imagePulled(Proxy proxy, Container container) {
        startupLog.get(proxy.getId()).getPullImage(container.getIndex()).stepSucceeded();
    }

    public void imagePulled(Proxy proxy, Container container, LocalDateTime pullingTime, LocalDateTime pulledTime) {
        startupLog.get(proxy.getId())
                .getPullImage(container.getIndex())
                .stepSucceeded(
                        pullingTime,
                        pulledTime
                );
    }

    /**
     * Step 3: container has been started and application is starting up
     */
    public void containerStarted(Proxy proxy, Container container) {
        startupLog.get(proxy.getId()).getStartContainer(container.getIndex()).stepSucceeded();
        startupLog.get(proxy.getId()).getStartApplication(container.getIndex()).stepStarted();
    }

    /**
     * Step 3 (fail): container could not be started
     */
    public void containerStartFailed(Proxy proxy, Container container) {
        startupLog.get(proxy.getId()).getStartContainer(container.getIndex()).stepFailed();
        startupLog.get(proxy.getId()).getCreateProxy().stepFailed();
    }


    /**
     *  Step 4: all containers has been started and all applications are running -> proxy has been started
     */
    public void proxyStarted(Proxy proxy) {
        for (Container container: proxy.getContainers()) {
            startupLog.get(proxy.getId()).getStartApplication(container.getIndex()).stepSucceeded();
        }
        startupLog.get(proxy.getId()).getCreateProxy().stepSucceeded();
    }

    /**
     * Step 4 (fail): one of the applications is unreachable -> it could not be started
     */
    public void applicationStartupFailed(Proxy proxy) {
        for (Container container: proxy.getContainers()) {
            startupLog.get(proxy.getId()).getStartApplication(container.getIndex()).stepFailed();
        }
        startupLog.get(proxy.getId()).getCreateProxy().stepFailed();
    }

    /**
     * Step 5: proxy has been removed (e.g. stopped by user, stopped because it failed to start ...)
     */
    public void proxyRemoved(Proxy proxy) {
        startupLog.remove(proxy.getId());
    }

    /**
     * Get the startup log of a specific container
     */
    public ProxyStartupLog getStartupLog(String proxyId) {
        return startupLog.get(proxyId);
    }

}
