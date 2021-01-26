/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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
import eu.openanalytics.containerproxy.service.EventService;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;

@Component
public class Micrometer implements IStatCollector {

    @Inject
    private MeterRegistry registry;

    private Timer appStartupTimer;

    private Timer appUsageTimer;

    private Counter appStartFailedCounter;

    private Counter authFailedCounter;

    private Counter userLogins;

    private Counter userLogouts;

    private Counter appStarts;

    private Counter appStops;

    @PostConstruct
    public void init() {
        userLogins = registry.counter("userLogins");
        userLogouts = registry.counter("userLogouts");
        appStarts = registry.counter("appStarts");
        appStops = registry.counter("appStops");
        appStartupTimer = registry.timer("startupTime");
        appUsageTimer = registry.timer("usageTime");
        appStartFailedCounter = registry.counter("startFailed");
        authFailedCounter = registry.counter("authFailed");
    }

    @Override
    public void accept(EventService.Event event, Environment env) throws IOException {
    }

    @EventListener
    public void onUserLogoutEvent(UserLogoutEvent event) {
        // TODO in a HA setup this event should only be processed by one server
        System.out.printf("UserLogoutEvent %s, %s, %s\n", event.getUserId(), event.getSessionId(), event.getWasExpired());
        userLogouts.increment();
    }

    @EventListener
    public void onUserLoginEvent(UserLoginEvent event) {
        System.out.printf("UserLoginEvent, %s, %s \n", event.getUserId(), event.getSessionId());
        userLogins.increment();
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) {
        System.out.printf("ProxyStartEvent %s ,%s\n", event.getUserId(), event.getStartupTime());

        appStarts.increment();
        appStartupTimer.record(event.getStartupTime());
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) {
        System.out.printf("ProxyStopEvent %s, %s\n", event.getUserId(), event.getUsageTime());

        appStops.increment();
        appUsageTimer.record(event.getUsageTime());
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) {
        System.out.printf("ProxyStartFailedEvent %s, %s\n", event.getUserId(), event.getSpecId());

        appStartFailedCounter.increment();
    }

    @EventListener
    public void onAuthFailedEvent(AuthFailedEvent event) {
        System.out.printf("AuthFailedEvent %s, %s\n", event.getUserId(), event.getSessionId());

        authFailedCounter.increment();
    }

}
