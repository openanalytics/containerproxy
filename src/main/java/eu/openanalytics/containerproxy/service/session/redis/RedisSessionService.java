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
import eu.openanalytics.containerproxy.service.session.ISessionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class RedisSessionService implements ISessionService  {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^.*sessions:([a-z0-9-]*)$");
    private static final int CACHE_UPDATE_INTERVAL = 60 * 1000; // update cache every minutes

    private final Log logger = LogFactory.getLog(RedisSessionService.class);

    @Inject
    private RedisIndexedSessionRepository redisIndexedSessionRepository;

    @Inject
    private RedisSessionConfig redisSessionConfig;

    private String keyPattern;
    private RedisTemplate<Object, Object> redisTemplate;

    private Integer cachedUsersLoggedInCount = null; // default value;

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

        Set<String> authenticatedUsers = keys
                .stream()
                .map((keyId) -> {
                    String sessionId = extractSessionId(keyId);
                    if (sessionId != null) {
                        logger.debug(String.format("Extracted sessionId %s ", sessionId));
                        Session session = redisIndexedSessionRepository.findById(sessionId);
                        if (session != null) {
                            Object object = session.getAttribute("SPRING_SECURITY_CONTEXT");
                            if (object instanceof SecurityContext) {
                                SecurityContext securityContext = (SecurityContext) object;
                                if (securityContext.getAuthentication().isAuthenticated()) {
                                    return securityContext.getAuthentication().getName();
                                }
                            }
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        logger.debug(String.format("Logged in users count %s, all users: %s ", authenticatedUsers.size(), authenticatedUsers));
        cachedUsersLoggedInCount = authenticatedUsers.size();
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

}
