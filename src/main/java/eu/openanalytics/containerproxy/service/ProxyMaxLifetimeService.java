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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This service releases proxies which reached their max-lifetime.
 */
@Service
public class ProxyMaxLifetimeService {

    private static final Integer CLEANUP_INTERVAL = 5 * 60 * 1000;
    private static final String PROP_DEFAULT_PROXY_MAX_LIFETIME = "proxy.default-proxy-max-lifetime";

    private final Logger log = LogManager.getLogger(ProxyMaxLifetimeService.class);

    @Inject
    private ProxyService proxyService;

    @Inject
    private Environment environment;

    private Long defaultMaxLifetime;

    @PostConstruct
    public void init() {
        defaultMaxLifetime = environment.getProperty(PROP_DEFAULT_PROXY_MAX_LIFETIME, Long.class, -1L);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                performCleanup();
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    private void performCleanup() {
        for (Proxy proxy : proxyService.getProxies(null, true)) {
            if (mustBeReleased(proxy)) {
                String uptime = DurationFormatUtils.formatDurationWords(
                        System.currentTimeMillis() - proxy.getCreatedTimestamp(),
                        true, false);
                log.info(String.format("Forcefully releasing proxy because it reached the max lifetime [user: %s] [spec: %s] [id: %s] [uptime: %s]", proxy.getUserId(), proxy.getSpec().getId(), proxy.getId(), uptime));
                proxyService.stopProxy(proxy, true, true);
            }
        }

    }

    private Boolean mustBeReleased(Proxy proxy) {
        if (proxy.getStatus() != ProxyStatus.Up) {
            return false;
        }

        Long maxLifeTime = proxy.getSpec().getMaxLifeTime();
        if (maxLifeTime == null) {
            maxLifeTime = defaultMaxLifetime;
        }

        if (maxLifeTime > 0) {
            Instant notBeforeTime = Instant.now().minus(maxLifeTime, ChronoUnit.MINUTES);

            return Instant.ofEpochMilli(proxy.getCreatedTimestamp()).isBefore(notBeforeTime);
        }

        return false;
    }

}
