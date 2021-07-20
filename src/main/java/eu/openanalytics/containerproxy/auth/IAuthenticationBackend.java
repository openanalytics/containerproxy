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
package eu.openanalytics.containerproxy.auth;

import java.util.Map;

import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;

import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

public interface IAuthenticationBackend {

	/**
	 * Get the name for this authentication backend, as used in the application.yml file.
	 */
	public String getName();

	/**
	 * Return true if this authentication backend supports authorization.
	 * In this context, authorization means the separation of permission levels
	 * via groups.
	 *
	 * If there is no authorization, all users have the same (administrator) permissions.
	 */
	public boolean hasAuthorization();

	/**
	 * Perform customization on the http level, such as filters and login forms.
	 */
	public void configureHttpSecurity(HttpSecurity http, AuthorizedUrl anyRequestConfigurer) throws Exception;

	/**
	 * Perform customization on the authentication manager level, such as authentication
	 * handling and authority population.
	 */
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception;

	public default String getLogoutSuccessURL() {
		return "/login";
	}

	public default String getLogoutURL() {
		return "/logout";
	}

	public default void customizeContainer(ContainerSpec spec) {
		// Default: do nothing.
	}

	public default void customizeContainerEnv(Map<String, String> env) {
		// Default: do nothing.
	}

	public default LogoutSuccessHandler getLogoutSuccessHandler() {
		SimpleUrlLogoutSuccessHandler urlLogoutHandler = new SimpleUrlLogoutSuccessHandler();
		urlLogoutHandler.setDefaultTargetUrl(getLogoutSuccessURL());
		return urlLogoutHandler;
	}
}
