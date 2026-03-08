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
package eu.openanalytics.containerproxy.auth.impl.spcs;

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
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Authentication filter for SPCS authentication.
 * Reads the Snowflake username from the Sf-Context-Current-User HTTP header
 * that Snowflake inserts when executeAsCaller: true is configured.
 * This filter only works when running inside Snowflake SPCS containers.
 */
public class SpcsAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SpcsAuthenticationFilter.class);

    // Prevent re-authentication on logout-success page and static resources
    private static final RequestMatcher REQUEST_MATCHER = new NegatedRequestMatcher(new OrRequestMatcher(
        new AntPathRequestMatcher("/logout-success"),
        new AntPathRequestMatcher("/webjars/**"),
        new AntPathRequestMatcher("/css/**")
    ));

    private static final String SPCS_INGRESS_USERNAME_HEADER = "Sf-Context-Current-User";
    private static final String SPCS_INGRESS_USERTOKEN_HEADER = "Sf-Context-Current-User-Token";

    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;

    public SpcsAuthenticationFilter(
            AuthenticationManager authenticationManager,
            ApplicationEventPublisher eventPublisher) {
        this.authenticationManager = authenticationManager;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain chain) throws ServletException, IOException {
        
        if (!REQUEST_MATCHER.matches(request)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // Get the username from Sf-Context-Current-User header (always present when running inside SPCS)
            String spcsIngressUserName = request.getHeader(SPCS_INGRESS_USERNAME_HEADER);
            
            // Get the user token (Sf-Context-Current-User-Token) if available (only when executeAsCaller=true)
            String spcsIngressUserToken = request.getHeader(SPCS_INGRESS_USERTOKEN_HEADER);
            
            if (spcsIngressUserName == null || spcsIngressUserName.isBlank()) {
                // Header is required - SPCS always adds this header to all requests when running inside SPCS
                // Fail authentication if header is missing
                throw new SpcsAuthenticationException("Required header " + SPCS_INGRESS_USERNAME_HEADER + " not found in request");
            }

            // Check if already authenticated with SPCS
            // This validation prevents session hijacking and ensures consistency:
            // - Security: Prevents an attacker from switching users mid-session by manipulating headers
            // - Performance: Avoids re-authenticating on every request once already authenticated
            // - Consistency: Ensures the session user matches the current request headers
            Authentication existingAuthentication = SecurityContextHolder.getContext().getAuthentication();
            if (existingAuthentication instanceof SpcsAuthenticationToken) {
                // Compare the username in the current request header with the username from the existing session
                // If they don't match, throw an exception (potential session hijacking or user switch)
                if (!existingAuthentication.getPrincipal().equals(spcsIngressUserName)) {
                    throw new SpcsAuthenticationException(
                        String.format("Username in header does not match existing session '%s'", 
                            existingAuthentication.getPrincipal()));
                } else {
                    // Same user: refresh SecurityContext with current request's token so downstream (e.g. addHeaders())
                    // always sees the latest Sf-Context-Current-User-Token; we avoid calling the provider for performance.
                    SpcsAuthenticationToken refreshedToken = new SpcsAuthenticationToken(
                            spcsIngressUserName,
                            spcsIngressUserToken,
                            new java.util.ArrayList<>(existingAuthentication.getAuthorities()),
                            new WebAuthenticationDetailsSource().buildDetails(request),
                            true);
                    SecurityContextHolder.getContext().setAuthentication(refreshedToken);
                    chain.doFilter(request, response);
                    return;
                }
            }

            // Create authentication token with username and token (token may be null)
            SpcsAuthenticationToken authRequest = new SpcsAuthenticationToken(
                spcsIngressUserName,
                spcsIngressUserToken,
                new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // Authenticate
            Authentication authResult = authenticationManager.authenticate(authRequest);
            if (authResult == null) {
                throw new SpcsAuthenticationException("No authentication result");
            }

            // Set in security context
            SecurityContextHolder.getContext().setAuthentication(authResult);
            eventPublisher.publishEvent(new AuthenticationSuccessEvent(authResult));
            logger.debug("Successfully authenticated SPCS user: {}", spcsIngressUserName);

        } catch (SpcsAuthenticationException e) {
            logger.warn("SPCS authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            // Don't continue the filter chain - fail the request
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("SPCS authentication failed: " + e.getMessage());
            return;
        } catch (AuthenticationException e) {
            logger.warn("SPCS authentication failed", e);
            SecurityContextHolder.clearContext();
            // Don't continue the filter chain - fail the request
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("SPCS authentication failed: " + e.getMessage());
            return;
        } catch (Exception e) {
            logger.warn("Unexpected error during SPCS authentication", e);
            SecurityContextHolder.clearContext();
            // Don't continue the filter chain - fail the request
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Unexpected error during SPCS authentication");
            return;
        }

        chain.doFilter(request, response);
    }

}
