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
package eu.openanalytics.containerproxy.auth.impl;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.spcs.SpcsAuthenticationFilter;
import eu.openanalytics.containerproxy.auth.impl.spcs.SpcsAuthenticationProvider;
import eu.openanalytics.containerproxy.auth.impl.spcs.SpcsAuthenticationToken;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.util.Map;

/**
 * Authentication backend for SPCS authentication.
 * 
 * This backend authenticates users based on HTTP headers that Snowflake forwards to services
 * running inside SPCS containers via ingress.
 * 
 * When a service is configured with executeAsCaller: true in its service specification,
 * Snowflake inserts the following headers in every incoming request:
 *   - Sf-Context-Current-User: The username of the calling user
 *   - Sf-Context-Current-User-Token: A token representing the calling user's context
 * 
 * The Sf-Context-Current-User-Token is automatically passed to child container services as
 * an HTTP header (Sf-Context-Current-User-Token) via ProxyMappingManager, allowing child
 * containers to access the caller's rights token for connecting to Snowflake.
 * 
 * Reference: https://docs.snowflake.com/en/developer-guide/snowpark-container-services/additional-considerations-services-jobs#configuring-caller-s-rights-for-your-service
 * 
 * This backend can only be used when running inside SPCS (detected by
 * SNOWFLAKE_SERVICE_NAME environment variable).
 * 
 * Note: ShinyProxy's SPCS backend automatically configures executeAsCaller: true for all services
 * (see SpcsBackend.buildServiceSpecYaml).
 * 
 * Configuration:
 *   proxy.authentication: spcs
 */
public class SpcsAuthenticationBackend implements IAuthenticationBackend {

    private static final Logger logger = LoggerFactory.getLogger(SpcsAuthenticationBackend.class);

    public static final String NAME = "spcs";

    private static final HttpString HEADER_USERNAME = new HttpString("Sf-Context-Current-User");
    private static final HttpString HEADER_USER_TOKEN = new HttpString("Sf-Context-Current-User-Token");

    private final SpcsAuthenticationFilter filter;

    public SpcsAuthenticationBackend(Environment environment, ApplicationEventPublisher applicationEventPublisher) {
        // Verify we're running inside SPCS
        String snowflakeServiceName = environment.getProperty("SNOWFLAKE_SERVICE_NAME");
        boolean runningInsideSpcs = snowflakeServiceName != null && !snowflakeServiceName.isEmpty();

        if (!runningInsideSpcs) {
            throw new IllegalStateException(
                "SpcsAuthenticationBackend can only be used when running inside SPCS. " +
                "SNOWFLAKE_SERVICE_NAME environment variable not found.");
        }

        logger.info("Initializing SPCS authentication backend (SNOWFLAKE_SERVICE_NAME: {})", snowflakeServiceName);

        // Create authentication provider
        SpcsAuthenticationProvider provider = new SpcsAuthenticationProvider(environment);
        ProviderManager providerManager = new ProviderManager(provider);
        filter = new SpcsAuthenticationFilter(providerManager, applicationEventPublisher);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasAuthorization() {
        return true;
    }

    @Override
    public void configureHttpSecurity(HttpSecurity http) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable);

        http.addFilterBefore(filter, AnonymousAuthenticationFilter.class)
            .exceptionHandling(e -> {
                // Empty configuration - the filter handles all authentication exceptions directly by returning
                // error responses and stopping the filter chain. This ensures no default Spring Security
                // exception handling (like redirects) occurs if any AuthenticationException somehow escapes.
            });
    }

    @Override
    public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
        // Nothing to do - authentication is handled by the filter
    }

    @Override
    public void customizeContainerEnv(Authentication user, Map<String, String> env) {
        // SPCS user token is passed as HTTP header per request, not as environment variable
        // See ProxyMappingManager.dispatchAsync() for header forwarding logic
    }

    @Override
    public String getLogoutSuccessURL() {
        return "/logout-success";
    }

    /**
     * Adds SPCS user context headers from the current request's authentication.
     * Called at request dispatch time (from HttpHeaders.getUndertowHeaderMap) so containers
     * receive the current user and token on every request, including after recovery.
     */
    public static HeaderMap addHeaders(Proxy proxy) {
        HeaderMap result = new HeaderMap();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof SpcsAuthenticationToken) {
            Object principal = auth.getPrincipal();
            if (principal != null) {
                String username = principal.toString();
                if (!username.isBlank()) {
                    result.put(HEADER_USERNAME, username);
                }
            }
            Object credentials = auth.getCredentials();
            if (credentials != null) {
                String userToken = credentials.toString();
                if (!userToken.isBlank()) {
                    result.put(HEADER_USER_TOKEN, userToken);
                }
            }
        }
        return result;
    }

}
