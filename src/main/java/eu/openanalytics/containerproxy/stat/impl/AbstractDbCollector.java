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
package eu.openanalytics.containerproxy.stat.impl;

import eu.openanalytics.containerproxy.event.AuthFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.event.UserLoginEvent;
import eu.openanalytics.containerproxy.event.UserLogoutEvent;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;

import java.io.IOException;

public abstract class AbstractDbCollector implements IStatCollector {

    @EventListener
    public void onUserLogoutEvent(UserLogoutEvent event) throws IOException {
        writeToDb(event, event.getTimestamp(), event.getUserId(), "Logout", null, event.getAuthentication());
    }

    @EventListener
    public void onUserLoginEvent(UserLoginEvent event) throws IOException {
        writeToDb(event, event.getTimestamp(), event.getUserId(), "Login", null, event.getAuthentication());
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) throws IOException {
        if (event.isLocalEvent()) {
            writeToDb(event,event.getTimestamp(), event.getUserId(), "ProxyStart", event.getSpecId(), event.getAuthentication());
        }
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) throws IOException {
        if (event.isLocalEvent()) {
            writeToDb(event, event.getTimestamp(), event.getUserId(), "ProxyStop", event.getSpecId(), event.getAuthentication());
        }
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) {
        // TODO
    }

    @EventListener
    public void onAuthFailedEvent(AuthFailedEvent event) {
        // TODO
    }

    protected abstract void writeToDb(ApplicationEvent event, long timestamp, String userId, String type, String data, Authentication authentication) throws IOException;

}
