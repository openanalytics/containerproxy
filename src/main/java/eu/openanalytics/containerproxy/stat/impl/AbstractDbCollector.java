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
package eu.openanalytics.containerproxy.stat.impl;

import eu.openanalytics.containerproxy.event.*;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import org.springframework.context.event.EventListener;

import java.io.IOException;

import static eu.openanalytics.containerproxy.event.BridgeableEvent.SOURCE_NOT_AVAILABLE;

public abstract class AbstractDbCollector implements IStatCollector {

    @EventListener
    public void onUserLogoutEvent(UserLogoutEvent event) throws IOException {
        writeToDb(event.getTimestamp(), event.getUserId(), "Logout", null);
    }

    @EventListener
    public void onUserLoginEvent(UserLoginEvent event) throws IOException {
        writeToDb(event.getTimestamp(), event.getUserId(), "Login", null);
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) throws IOException {
        if (event.getSource().equals(SOURCE_NOT_AVAILABLE)) {
            writeToDb(event.getTimestamp(), event.getUserId(), "ProxyStart", event.getSpecId());
        }
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) throws IOException {
        if (event.getSource().equals(SOURCE_NOT_AVAILABLE)) {
            writeToDb(event.getTimestamp(), event.getUserId(), "ProxyStop", event.getSpecId());
        }
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) throws IOException {
        // TODO
    }

    @EventListener
    public void onAuthFailedEvent(AuthFailedEvent event) throws IOException {
        // TODO
    }

    protected abstract void writeToDb(long timestamp, String userId, String type, String data) throws IOException;

}
