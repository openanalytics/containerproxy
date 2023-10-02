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
package eu.openanalytics.containerproxy.stat;

import eu.openanalytics.containerproxy.event.*;
import org.springframework.context.event.EventListener;

import java.io.IOException;

public interface IStatCollector {

    @EventListener
    default public void onUserLogoutEvent(UserLogoutEvent event) throws IOException {
    }

    @EventListener
    default public void onUserLoginEvent(UserLoginEvent event) throws IOException {
    }

    @EventListener
    default public void onProxyStartEvent(ProxyStartEvent event) throws IOException {
    }

    @EventListener
    default public void onProxyStopEvent(ProxyStopEvent event) throws IOException {
    }

    @EventListener
    default public void onProxyStartFailedEvent(ProxyStartFailedEvent event) throws IOException {
    }

    @EventListener
    default public void onAuthFailedEvent(AuthFailedEvent event) throws IOException {
    }

}
