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
package eu.openanalytics.containerproxy.stat.impl;

import eu.openanalytics.containerproxy.event.*;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.stat.IStatCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

public class Micrometer implements IStatCollector {

    @Inject
    private MeterRegistry registry;

    @Inject
    private ProxyService proxyService;

    private final Logger logger = LogManager.getLogger(getClass());

    private Counter appStartFailedCounter;

    private Counter authFailedCounter;

    private Counter userLogins;

    private Counter userLogouts;

    @PostConstruct
    public void init() {

        userLogins = registry.counter("userLogins");
        userLogouts = registry.counter("userLogouts");
        appStartFailedCounter = registry.counter("startFailed");
        authFailedCounter = registry.counter("authFailed");

        for (ProxySpec spec : proxyService.getProxySpecs(null, true)) {
            registry.counter("appStarts", "spec.id", spec.getId());
            registry.counter("appStops", "spec.id", spec.getId());
            registry.timer("startupTime", "spec.id", spec.getId());
            registry.timer("usageTime", "spec.id", spec.getId());
        }
    }

    @EventListener
    public void onUserLogoutEvent(UserLogoutEvent event) {
        logger.debug("UserLogoutEvent [user: {}, sessionId: {}, expired: {}]", event.getUserId(), event.getSessionId(), event.getWasExpired());
        userLogouts.increment();
    }

    @EventListener
    public void onUserLoginEvent(UserLoginEvent event) {
        logger.debug("UserLoginEvent [user: {}, sessionId: {}]", event.getUserId(), event.getSessionId());
        userLogins.increment();
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) {
        logger.debug("ProxyStartEvent [user: {}, startupTime: {}]", event.getUserId(), event.getStartupTime());
        registry.counter("appStarts", "spec.id", event.getSpecId()).increment();
        registry.timer("startupTime", "spec.id", event.getSpecId()).record(event.getStartupTime());
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) {
        logger.debug("ProxyStopEvent [user: {}, usageTime: {}]", event.getUserId(), event.getUsageTime());
        registry.counter("appStops", "spec.id", event.getSpecId()).increment();
        registry.timer("usageTime", "spec.id", event.getSpecId()).record(event.getUsageTime());
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) {
        logger.debug("ProxyStartFailedEvent [user: {}, specId: {}]", event.getUserId(), event.getSpecId());
        appStartFailedCounter.increment();
    }

    @EventListener
    public void onAuthFailedEvent(AuthFailedEvent event) {
        logger.debug("AuthFailedEvent [user: {}, sessionId: {}]", event.getUserId(), event.getSessionId());
        authFailedCounter.increment();
    }

}
