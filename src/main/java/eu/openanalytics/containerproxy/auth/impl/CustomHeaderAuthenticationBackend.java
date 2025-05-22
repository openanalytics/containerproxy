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
import eu.openanalytics.containerproxy.auth.impl.customHeader.CustomHeaderAuthenticationFilter;
import eu.openanalytics.containerproxy.auth.impl.customHeader.CustomHeaderAuthenticationProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public class CustomHeaderAuthenticationBackend implements IAuthenticationBackend {

    public final static String NAME = "customHeader";

    private final static String PROP_CUSTOM_AUTH_USERNAME_HEADER_NAME = "proxy.custom-header.username-header-name";
    private final static String DEFAULT_USERNAME_HEADER_NAME = "REMOTE_USER";

    private final CustomHeaderAuthenticationFilter filter;

    public CustomHeaderAuthenticationBackend(Environment environment, ApplicationEventPublisher applicationEventPublisher) {
        String usernameHeaderName = environment.getProperty(PROP_CUSTOM_AUTH_USERNAME_HEADER_NAME, DEFAULT_USERNAME_HEADER_NAME);
        ProviderManager providerManager = new ProviderManager(new CustomHeaderAuthenticationProvider());
        filter = new CustomHeaderAuthenticationFilter(providerManager, applicationEventPublisher, usernameHeaderName);
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
                e.authenticationEntryPoint((request, response, authException) -> {
                    response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/auth-error").build().toUriString());
                });
            });
    }

    @Override
    public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
        // Nothing to do.
    }

    @Override
    public String getLogoutSuccessURL() {
        return "/logout-success";
    }


}
