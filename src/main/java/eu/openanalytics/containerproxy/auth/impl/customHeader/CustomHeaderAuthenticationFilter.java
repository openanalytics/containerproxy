/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
package eu.openanalytics.containerproxy.auth.impl.customHeader;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Nonnull;
import java.io.IOException;

public class CustomHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;

    // prevent re-login on logout-success page
    private static final RequestMatcher REQUEST_MATCHER = new NegatedRequestMatcher(new OrRequestMatcher(
        new AntPathRequestMatcher("/logout-success"),
        new AntPathRequestMatcher("/webjars/**"),
        new AntPathRequestMatcher("/css/**"))
    );

    private final String usernameHeaderName;

    public CustomHeaderAuthenticationFilter(AuthenticationManager authenticationManager, ApplicationEventPublisher eventPublisher, String usernameHeaderName) {
        this.authenticationManager = authenticationManager;
        this.eventPublisher = eventPublisher;
        this.usernameHeaderName = usernameHeaderName;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain chain) throws ServletException, IOException, AuthenticationException {
        if (!REQUEST_MATCHER.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String remoteUser = request.getHeader(usernameHeaderName);
            if (remoteUser == null) {
                throw new CustomHeaderAuthenticationException(String.format("Missing username header '%s'", usernameHeaderName));
            }

            Authentication existingAuthentication = SecurityContextHolder.getContext().getAuthentication();
            if (existingAuthentication instanceof CustomHeaderAuthenticationToken) {
                if (!existingAuthentication.getPrincipal().equals(remoteUser)) {
                    throw new CustomHeaderAuthenticationException(String.format("Username in header '%s' does not match existing session '%s'", remoteUser, existingAuthentication.getPrincipal()));
                } else {
                    chain.doFilter(request, response);
                    return;
                }
            }

            Authentication authRequest = new CustomHeaderAuthenticationToken(remoteUser, false);
            Authentication authResult = authenticationManager.authenticate(authRequest);
            if (authResult == null) {
                throw new CustomHeaderAuthenticationException("No authentication");
            }

            SecurityContextHolder.getContext().setAuthentication(authResult);
            eventPublisher.publishEvent(new AuthenticationSuccessEvent(authResult));
        } catch (CustomHeaderAuthenticationException e) {
            logger.warn("Authentication failed", e);
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

}
