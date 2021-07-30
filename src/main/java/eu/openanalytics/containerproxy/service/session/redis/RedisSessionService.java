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
package eu.openanalytics.containerproxy.service.session.redis;

import eu.openanalytics.containerproxy.RedisSessionConfig;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import eu.openanalytics.containerproxy.service.session.AbstractSessionService;
import eu.openanalytics.containerproxy.service.session.ISessionService;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class RedisSessionService extends AbstractSessionService {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^.*sessions:([a-z0-9-]*)$");
    private static final int CACHE_UPDATE_INTERVAL = 20 * 1000; // update cache every minutes

    private final Log logger = LogFactory.getLog(RedisSessionService.class);

    @Inject
    private RedisIndexedSessionRepository redisIndexedSessionRepository;

    @Inject
    private RedisSessionConfig redisSessionConfig;

    private String keyPattern;
    private RedisTemplate<Object, Object> redisTemplate;

    private Integer cachedUsersLoggedInCount = null; // default value;
    private Integer cachedActiveUsersCount = null; // default value;

    @PostConstruct
    public void init() {
        keyPattern = redisSessionConfig.getNamespace() + ":sessions:*";
        redisTemplate = (RedisTemplate<Object, Object>) redisIndexedSessionRepository.getSessionRedisOperations();
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
        Session session = redisIndexedSessionRepository.findById(sessionId);//l.setLastAccessedTime
        session.setLastAccessedTime(Instant.now());
    }

    @Override
    public String extractSessionIdFromExchange(HttpServerExchange exchange) {
        ServletRequestContext attachment = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        HttpSession session = (HttpSession) attachment.getServletRequest().getAttribute("org.springframework.session.SessionRepository.CURRENT_SESSION");
        return session.getId();
    }

    /**
     * Updates the cached count of users.
     * We only update this value every CACHE_UPDATE_INTERVAL because this is a relative heavy computation to do.
     * Therefore we don't want that this calculation is performed every time
     * {@link RedisSessionService#getLoggedInUsersCount()} is called. Especially since this function could be called
     * using an HTTP request.
     * See the warning at https://redis.io/commands/keys .
     */
    private void updateCachedUsersLoggedInCount() {
        Set<Object> keys = redisTemplate.keys(keyPattern);

        if (keys == null) {
            return;
        }

        // users are only counted as active, if they are active after this time.
        Instant minimumInstantTime = Instant.now().minusSeconds(60);

        Set<String> authenticatedUsers = new HashSet<>();
        Set<String> activeUsers = new HashSet<>();

        for (Object keyId : keys) {
            String sessionId = extractSessionId(keyId);
            if (sessionId == null) continue;

            Session session = redisIndexedSessionRepository.findById(sessionId);
            if (session == null) continue;

            String authenticationName = extractAuthName(extractAuthenticationIfAuthenticated(session), sessionId);
            if (authenticationName == null) continue;

            authenticatedUsers.add(authenticationName);

            if (session.getLastAccessedTime().isAfter(minimumInstantTime)) {
                activeUsers.add(authenticationName);
            }
        }

        logger.debug(String.format("Logged in users count %s, all users: %s ", authenticatedUsers.size(), authenticatedUsers));
        cachedUsersLoggedInCount = authenticatedUsers.size();
        logger.debug(String.format("Active users count %s, %s ", activeUsers.size(), activeUsers));
        cachedActiveUsersCount = activeUsers.size();
    }

    /**
     * Extracts the sessionId from the Redis Key
     */
    private String extractSessionId(Object keyId) {
        if (keyId instanceof String) {
            Matcher matcher = SESSION_ID_PATTERN.matcher((String) keyId);

            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Extracts the {@link Authentication} object from the given Session, if and only if the user is authenticated.
     * @param session the session
     * @return Authentication|null
     */
    private Authentication extractAuthenticationIfAuthenticated(Session session) {
        Object object = session.getAttribute("SPRING_SECURITY_CONTEXT");

        if (object instanceof SecurityContext) {
            SecurityContext securityContext = (SecurityContext) object;

            if (securityContext.getAuthentication().isAuthenticated()) {

                return securityContext.getAuthentication();
            }
        }

        return null;
    }

}
