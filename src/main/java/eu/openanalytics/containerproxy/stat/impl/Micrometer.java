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
import eu.openanalytics.containerproxy.session.ISessionInformation;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class Micrometer implements IStatCollector {

    @Inject
    private MeterRegistry registry;

    @Inject
    private ISessionInformation sessionInformation;

    private AtomicInteger appsGauge;

    private AtomicLong loggedInUsersGauge;

    private Timer appStartupTimer;

    private Timer appUsageTimer;

    private Counter appStartFailedCounter;

    private Counter authFailedCounter;

    @PostConstruct
    public void init() {
        appsGauge = registry.gauge("apps", new AtomicInteger(0));
        loggedInUsersGauge = registry.gauge("users", new AtomicLong(0));
        appStartupTimer = registry.timer("startupTime");
        appUsageTimer = registry.timer("usageTime");
        appStartFailedCounter = registry.counter("startFailed");
        authFailedCounter = registry.counter("authFailed");
    }

    @Override
    public void accept(EventService.Event event, Environment env) throws IOException {
        if (event.type.equals(EventService.EventType.ProxyStart.toString())) {
            appsGauge.incrementAndGet();
        } else if (event.type.equals(EventService.EventType.ProxyStop.toString())) {
            appsGauge.decrementAndGet();
        } else {
            System.out.println("not processing events of this type yet");
        }
    }

    @EventListener
    public void onUserLogoutEvent(UserLogoutEvent event) {
        loggedInUsersGauge.set(sessionInformation.getLoggedInUsersCount());
    }

    @EventListener
    public void onUserLoginEvent(UserLoginEvent event) {
        loggedInUsersGauge.set(sessionInformation.getLoggedInUsersCount());
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) {
        System.out.printf("ProxyStartEvent %s, %s", event.getUserId(), event.getStartupTime());

        appStartupTimer.record(event.getStartupTime());
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) {
        System.out.printf("ProxyStopEvent %s, %s", event.getUserId(), event.getUsageTime());

        appUsageTimer.record(event.getUsageTime());
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) {
        System.out.printf("ProxyStartFailedEvent %s, %s", event.getUserId(), event.getSpecId());

        appStartFailedCounter.increment();
    }

    @EventListener
    public void onAuthFailedEvent(AuthFailedEvent event) {
        System.out.printf("AuthFailedEvent %s, %s", event.getUserId(), event.getSessionId());

        authFailedCounter.increment();
    }


}
