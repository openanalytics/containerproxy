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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;

/**
 * This authentication type does nothing by itself. All social configuration is handled in
 * {@link eu.openanalytics.containerproxy.security.SocialSecurityConfig}
 */
public class SocialAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "social";

	public SocialAuthenticationBackend() {
		Logger logger = LoggerFactory.getLogger(getClass());
		logger.warn("WARNING: ###");
		logger.warn("WARNING: ###");
		logger.warn("WARNING: Social authentication is deprecated and will be removed in the next version (3.1.0)!");
		logger.warn("WARNING: ###");
		logger.warn("WARNING: ###");
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
	public void configureHttpSecurity(HttpSecurity http, AuthorizedUrl anyRequestConfigurer) throws Exception {
		// Nothing to do.
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		// Configure a no-op authentication.
		auth.inMemoryAuthentication();
	}

}
