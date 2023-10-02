/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.InMemoryUserDetailsManagerConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;

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
	public void configureHttpSecurity(HttpSecurity http, AuthorizedUrl anyRequestConfigurer) throws Exception {
		// Nothing to do.
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		InMemoryUserDetailsManagerConfigurer<AuthenticationManagerBuilder> userDetails = auth.inMemoryAuthentication();
		int i=0;
		SimpleUser user = loadUser(i++);
		while (user != null) {
			userDetails.withUser(user.name).password("{noop}" + user.password).roles(user.roles);
			user = loadUser(i++);
		}
	}
	
	private SimpleUser loadUser(int index) {
		String userName = environment.getProperty(String.format("proxy.users[%d].name", index));
		if (userName == null) return null;
		String password = environment.getProperty(String.format("proxy.users[%d].password", index));

		// method 1: single property with comma seperated groups
		String[] groups = environment.getProperty(String.format("proxy.users[%d].groups", index), String[].class);
		if (groups != null) {
			groups = Arrays.stream(groups).map(String::toUpperCase).toArray(String[]::new);
			return new SimpleUser(userName, password, groups);
		} else {
			// method 2: YAML array
			List<String> groupsList = new ArrayList<>();
			int groupIndex = 0;
			String group = environment.getProperty(String.format("proxy.users[%d].groups[%d]", index, groupIndex));
			while (group != null) {
				groupsList.add(group.toUpperCase());
				groupIndex++;
				group = environment.getProperty(String.format("proxy.users[%d].groups[%d]", index, groupIndex));
			}
			return new SimpleUser(userName, password, groupsList.toArray(new String[0]));
		}
	}
	
	private static class SimpleUser {
		
		public String name;
		public String password;
		public String[] roles;
		
		public SimpleUser(String name, String password, String[] roles) {
			this.name = name;
			this.password = password;
			this.roles = roles;
		}
		
	}
}
