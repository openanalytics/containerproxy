/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
package eu.openanalytics.containerproxy.stat;

import eu.openanalytics.containerproxy.event.AuthFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.event.UserLoginEvent;
import eu.openanalytics.containerproxy.event.UserLogoutEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;

public interface IStatCollector {

    @EventListener
    default void onUserLogoutEvent(UserLogoutEvent event) throws IOException {
    }

    @EventListener
    default void onUserLoginEvent(UserLoginEvent event) throws IOException {
    }

    @EventListener
    default void onProxyStartEvent(ProxyStartEvent event) throws IOException {
    }

    @EventListener
    default void onProxyStopEvent(ProxyStopEvent event) throws IOException {
    }

    @EventListener
    default void onProxyStartFailedEvent(ProxyStartFailedEvent event) {
    }

    @EventListener
    default void onAuthFailedEvent(AuthFailedEvent event) {
    }

}
