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
import eu.openanalytics.containerproxy.util.EnvironmentUtils;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.InMemoryUserDetailsManagerConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simple authentication method where user/password combinations are
 * provided by the application.yml file.
 */
public class SimpleAuthenticationBackend implements IAuthenticationBackend {

    public static final String NAME = "simple";

    @Inject
    private Environment environment;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasAuthorization() {
        return true;
    }

    @Override
    public void configureHttpSecurity(HttpSecurity http) {
        // Nothing to do.
    }

    @Override
    public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
        InMemoryUserDetailsManagerConfigurer<AuthenticationManagerBuilder> userDetails = auth.inMemoryAuthentication();
        int i = 0;
        SimpleUser user = loadUser(i++);
        while (user != null) {
            userDetails
                .withUser(user.name)
                .password("{noop}" + user.password)
                .roles(user.roles);
            user = loadUser(i++);
        }
    }

    private SimpleUser loadUser(int index) {
        String userName = environment.getProperty(String.format("proxy.users[%d].name", index));
        if (userName == null) return null;
        String password = environment.getProperty(String.format("proxy.users[%d].password", index));

        // method 1: single property with comma seperated groups
        List<String> groups = EnvironmentUtils.readList(environment, String.format("proxy.users[%d].groups", index));
        if (groups != null) {
            return new SimpleUser(userName, password, groups.toArray(new String[0]));
        } else {
            return new SimpleUser(userName, password, new String[]{});
        }
    }

    private static class SimpleUser {

        public final String name;
        public final String password;
        public final String[] roles;

        public SimpleUser(String name, String password, String[] roles) {
            this.name = name;
            this.password = password;
            this.roles = roles;
        }

    }
}
