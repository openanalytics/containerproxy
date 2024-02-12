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
package eu.openanalytics.containerproxy.util;

import eu.openanalytics.containerproxy.event.ProxyPauseEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public interface ICleanupStoppedProxies {

    @Async
    @EventListener
    default void onProxyStopEvent(ProxyStopEvent event) {
        cleanupProxy(event.getProxyId());
    }

    @Async
    @EventListener
    default void onProxyPauseEvent(ProxyPauseEvent event) {
        cleanupProxy(event.getProxyId());
    }

    void cleanupProxy(String proxyId);

}
