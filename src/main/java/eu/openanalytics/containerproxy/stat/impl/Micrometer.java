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

import eu.openanalytics.containerproxy.service.EventService;
import eu.openanalytics.containerproxy.session.ISessionInformation;
import eu.openanalytics.containerproxy.session.UserSessionLogoutEvent;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionDestroyedEvent;
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

    @PostConstruct
    public void init() {
        appsGauge = registry.gauge("apps", new AtomicInteger(0));
        loggedInUsersGauge = registry.gauge("users", new AtomicLong(0));
    }

    @Override
    public void accept(EventService.Event event, Environment env) throws IOException {
        if (event.type.equals(EventService.EventType.ProxyStart.toString())) {
            appsGauge.incrementAndGet();
        } else if (event.type.equals(EventService.EventType.ProxyStop.toString())) {
            appsGauge.decrementAndGet();
        } else if (event.type.equals(EventService.EventType.Login.toString())
                || event.type.equals(EventService.EventType.Logout.toString())) {
            System.out.println(String.format("Event: %s", event.type));
            loggedInUsersGauge.set(sessionInformation.getLoggedInUsersCount());
        } else {
            System.out.println("not processing events of this type yet");
        }
    }

    @EventListener
    public void onUserSessionLogoutEvent(UserSessionLogoutEvent event) {
        System.out.println("Update micrometer");
        loggedInUsersGauge.set(sessionInformation.getLoggedInUsersCount());
    }

}
