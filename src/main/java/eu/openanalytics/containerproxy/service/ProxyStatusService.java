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
    public void proxyCreated(String proxyId) {
        ProxyStartupLog proxyStartupLog = new ProxyStartupLog();
        proxyStartupLog.getCreateProxy().stepStarted();
        startupLog.put(proxyId, proxyStartupLog);
    }

    /**
     * Step 2: container has been created and is starting up
     */
    public void containerStarting(String proxyId, int containerIdx) {
        startupLog.get(proxyId).getStartContainer(containerIdx).stepStarted();
    }

    /**
     * Step 2.1: schedule container
     */
    public void containerScheduled(String proxyId, int containerIdx, LocalDateTime scheduledTime) {
        startupLog.get(proxyId)
                .getScheduleContainer(containerIdx)
                .stepSucceeded(
                    startupLog.get(proxyId).getStartContainer(containerIdx).getStartTime(),
                    scheduledTime
                );
    }

    /**
     * Step 2.2: pull image
     */
    public void imagePulling(String proxyId, int containerIdx) {
        startupLog.get(proxyId).getPullImage(containerIdx).stepStarted();
    }

    public void imagePulled(String proxyId, int containerIdx) {
        startupLog.get(proxyId).getPullImage(containerIdx).stepSucceeded();
    }

    public void imagePulled(String proxyId, int containerIdx, LocalDateTime pullingTime, LocalDateTime pulledTime) {
        startupLog.get(proxyId)
                .getPullImage(containerIdx)
                .stepSucceeded(
                        pullingTime,
                        pulledTime
                );
    }

    /**
     * Step 3: container has been started and application is starting up
     */
    public void containerStarted(String proxyId, int containerIdx) {
        startupLog.get(proxyId).getStartContainer(containerIdx).stepSucceeded();
        startupLog.get(proxyId).getStartApplication(containerIdx).stepStarted();
    }

    /**
     * Step 3 (fail): container could not be started
     */
    public void containerStartFailed(String proxyId, int containerIdx) {
        startupLog.get(proxyId).getStartContainer(containerIdx).stepFailed();
        startupLog.get(proxyId).getCreateProxy().stepFailed();
    }


    /**
     *  Step 4: all containers has been started and all applications are running -> proxy has been started
     */
    public void proxyStarted(String proxyId, int numContainers) {
        for (int i = 0; i < numContainers; i++) {
            startupLog.get(proxyId).getStartApplication(i).stepSucceeded();
        }
        startupLog.get(proxyId).getCreateProxy().stepSucceeded();
    }

    /**
     * Step 4 (fail): one of the applications is unreachable -> it could not be started
     */
    public void applicationStartupFailed(String proxyId, int numContainers) {
        for (int i = 0; i < numContainers; i++) {
            startupLog.get(proxyId).getStartApplication(i).stepFailed();
        }
        startupLog.get(proxyId).getCreateProxy().stepFailed();
    }

    /**
     * Step 5: proxy has been removed (e.g. stopped by user, stopped because it failed to start ...)
     */
    public void proxyRemoved(String proxyId) {
        startupLog.remove(proxyId);
    }

    /**
     * Get the startup log of a specific container
     */
    public ProxyStartupLog getStartupLog(String proxyId) {
        return startupLog.get(proxyId);
    }

}
