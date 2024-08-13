/**
 * ContainerProxy
 *
<<<<<<< HEAD
 * Copyright (C) 2016-2024 Open Analytics
=======
 * Copyright (C) 2016-2023 Open Analytics
>>>>>>> d57455466c9e5d7069e2878c7b751ec110a99b8c
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

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.inject.Inject;
<<<<<<< HEAD
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
=======
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
>>>>>>> d57455466c9e5d7069e2878c7b751ec110a99b8c

import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CustomHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final Logger log = LogManager.getLogger(CustomHeaderAuthenticationFilter.class);

    private final RequestMatcher requestMatcher = new OrRequestMatcher(
            new AntPathRequestMatcher("/app/**"),
            new AntPathRequestMatcher("/app_i/**"),
            new AntPathRequestMatcher("/**"));

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
            @Nonnull FilterChain chain) throws ServletException, IOException {

        log.debug(String.format("CustomHeaderAuthenticationFilter CALLED"));
        if (requestMatcher.matches(request)) {
            String remoteUser = request.getHeader("REMOTE_USER");
            log.debug(String.format("CustomHeaderAuthenticationFilter REMOTE_USER: %s", remoteUser));
            try {
                Authentication authRequest = new CustomHeaderAuthenticationToken(remoteUser);
                SecurityContextHolder.getContext().setAuthentication(authRequest);
            } catch (AuthenticationException e) {
                throw e;
            }

        }
        chain.doFilter(request, response);
    }
}