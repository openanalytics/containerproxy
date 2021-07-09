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
package eu.openanalytics.containerproxy.service.session.undertow;

import eu.openanalytics.containerproxy.service.session.ISessionService;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "none")
public class UndertowSessionService implements ISessionService {

    private static final int CACHE_UPDATE_INTERVAL = 60 * 1000; // update cache every minutes

    private final Log logger = LogFactory.getLog(UndertowSessionService.class);

    @Inject
    private CustomSessionManagerFactory customInMemorySessionManagerFactory;

    // default value, note we cannot use 0 or -1 here as that would cause a dip when restarting ShinyProxy
    private Integer cachedUsersLoggedInCount = null;

    @PostConstruct
    public void init() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateCachedUsersLoggedInCount();
            }
        }, 0, CACHE_UPDATE_INTERVAL);
    }

    @Override
    public Integer getLoggedInUsersCount() {
        return cachedUsersLoggedInCount;
    }

    /**
     * Updates the cached count of users.
     * We only update this value every CACHE_UPDATE_INTERVAL because this is a relative heavy computation to do.
     * Therefore we don't want that this calculation is performed every time
     * {@link UndertowSessionService#getLoggedInUsersCount()} is called. Especially since this function could be called
     * using an HTTP request.
     * This function does not use external databases (in contrast to
     * {@link eu.openanalytics.containerproxy.service.session.redis.RedisSessionService}, but still it needs to loop
     * over all sessions in the Servlet).
     */
    private void updateCachedUsersLoggedInCount() {
        InMemorySessionManager instance = this.customInMemorySessionManagerFactory.getInstance();
        if (instance == null) {
            return;
        }

        Set<String> authenticatedUsers = instance
                .getAllSessions()
                .stream()
                .map((sessionId) -> {
                    Session sessionImpl = instance.getSession(sessionId);
                    if (sessionImpl == null) return null;
                    Object object = sessionImpl.getAttribute("SPRING_SECURITY_CONTEXT");
                    if (object instanceof SecurityContext) {
                        SecurityContext securityContext = (SecurityContext) object;
                        if (securityContext.getAuthentication().isAuthenticated()) {
                            return securityContext.getAuthentication().getName();
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        logger.debug(String.format("Logged in users count %s, all users: %s ", authenticatedUsers.size(), authenticatedUsers));
        cachedUsersLoggedInCount = authenticatedUsers.size();
    }


}
