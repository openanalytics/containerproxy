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

import eu.openanalytics.containerproxy.service.session.AbstractSessionService;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;
import org.apache.commons.lang.reflect.FieldUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

@Component
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "none")
public class UndertowSessionService extends AbstractSessionService {

    private static final int CACHE_UPDATE_INTERVAL = 20 * 1000; // update cache every minutes

    private final Log logger = LogFactory.getLog(UndertowSessionService.class);

    @Inject
    private CustomSessionManagerFactory customInMemorySessionManagerFactory;

    // default value, note we cannot use 0 or -1 here as that would cause a dip when restarting ShinyProxy
    private Integer cachedUsersLoggedInCount = null;

    private Integer cachedActiveUsersCount = null;

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

    @Override
    public Integer getActiveUsersCount() {
        return cachedActiveUsersCount;
    }

    @Override
    public void reActivateSession(String sessionId) {
        Session session = customInMemorySessionManagerFactory.getInstance().getSession(sessionId);
        if (session == null) {
            return;
        }
        try {
            // TODO: this is hack we would prefer not to use, let's discuss with Undertow developers to provide
            // a method similar to Spring's session setLastAccessedTime() method
            FieldUtils.writeField(session, "lastAccessed", System.currentTimeMillis(), true);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        // called for the SessionImpl.bumpTimeout() method
        session.requestDone(new HttpServerExchange(null));
    }

    @Override
    public String extractSessionIdFromExchange(HttpServerExchange exchange) {
        ServletRequestContext attachment = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        return attachment.getSession().getId();
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

        // users are only counted as active, if they are active after this time.
        Instant minimumInstantTime = Instant.now().minusSeconds(60);

        Set<String> authenticatedUsers = new HashSet<>();
        Set<String> activeUsers = new HashSet<>();

        for (String sessionId : instance.getAllSessions()) {
            Session session = instance.getSession(sessionId);
            if (session == null) continue;

            String authenticationName = extractAuthName(extractAuthenticationIfAuthenticated(session), sessionId);
            if (authenticationName == null) continue;

            authenticatedUsers.add(authenticationName);

            if (Instant.ofEpochMilli(session.getLastAccessedTime()).isAfter(minimumInstantTime)) {
                activeUsers.add(authenticationName);
            }
        }

        logger.debug(String.format("Logged in users count %s, all users: %s ", authenticatedUsers.size(), authenticatedUsers));
        cachedUsersLoggedInCount = authenticatedUsers.size();
        logger.debug(String.format("Active users count %s, %s ", activeUsers.size(), activeUsers));
        cachedActiveUsersCount = activeUsers.size();
    }


    /**
     * Extracts the {@link Authentication} object from the given Session, if and only if the user is authenticated.
     *
     * @param session the session
     * @return Authentication|null
     */
    private Authentication extractAuthenticationIfAuthenticated(Session session) {
        Object object = session.getAttribute("SPRING_SECURITY_CONTEXT");

        if (object instanceof SecurityContext) {
            return ((SecurityContext) object).getAuthentication();
        }

        return null;
    }

}