/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
package eu.openanalytics.containerproxy.auth.impl;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.session.Session;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * NoOp authentication: no login is required, all apps are public.
 */
public class NoAuthenticationBackend implements IAuthenticationBackend {

    public static final String NAME = "none";
    private static final String ANONYMOUS_USERID_SESSION_ATTRIBUTE = "ANONYMOUS_USER_ID";
    private final ApplicationEventPublisher applicationEventPublisher;

    public NoAuthenticationBackend(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public static String extractUserId(Session session) {
        return session.getAttribute(ANONYMOUS_USERID_SESSION_ATTRIBUTE);
    }

    public static String extractUserId(io.undertow.server.session.Session session) {
        return (String) session.getAttribute(ANONYMOUS_USERID_SESSION_ATTRIBUTE);
    }

    public static String extractUserId(HttpSession session) {
        return (String) session.getAttribute(ANONYMOUS_USERID_SESSION_ATTRIBUTE);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasAuthorization() {
        return false;
    }

    @Override
    public void configureHttpSecurity(HttpSecurity http) throws Exception {
        http.anonymous(anonymous -> {
            anonymous.authenticationFilter(new Filter(applicationEventPublisher, UUID.randomUUID().toString()));
        });
    }

    @Override
    public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
        // Configure a no-op authentication.
        auth.inMemoryAuthentication();
    }

    /**
     * Extension of AnonymousAuthenticationFilter that generates a unique username for every user.
     */
    private static class Filter extends AnonymousAuthenticationFilter {

        private final WebAuthenticationDetailsSource webAuthenticationDetailsSource = new WebAuthenticationDetailsSource();
        private final String key;
        private final ApplicationEventPublisher applicationEventPublisher;

        public Filter(ApplicationEventPublisher applicationEventPublisher, String key) {
            super(key);
            this.key = key;
            this.applicationEventPublisher = applicationEventPublisher;
        }

        /**
         * Overrides the function that creates an Authentication token for an anonymous user.
         * A unique userId is stored in the session of the user and then used as name for the Authentication token.
         */
        protected Authentication createAuthentication(HttpServletRequest request) {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr == null) {
                // actuator endpoints have no session/attrs -> just return
                AnonymousAuthenticationToken token = new AnonymousAuthenticationToken(key, getPrincipal(), getAuthorities());
                token.setDetails(webAuthenticationDetailsSource.buildDetails(request));
                return token;
            }
            HttpSession session = attr.getRequest().getSession(true); // true == allow create
            String userIdAttribute = (String) session.getAttribute(ANONYMOUS_USERID_SESSION_ATTRIBUTE);
            String userId;
            if (userIdAttribute == null) {
                userId = UUID.randomUUID().toString();
                session.setAttribute(ANONYMOUS_USERID_SESSION_ATTRIBUTE, userId);
            } else {
                userId = userIdAttribute;
            }
            AnonymousAuthenticationToken token = new AnonymousAuthenticationToken(key, userId, getAuthorities());
            token.setDetails(webAuthenticationDetailsSource.buildDetails(request));

            if (userIdAttribute == null) {
                // new user -> publish event
                applicationEventPublisher.publishEvent(new AuthenticationSuccessEvent(token));
            }

            return token;
        }

    }

}
