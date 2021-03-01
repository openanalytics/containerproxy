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
package eu.openanalytics.containerproxy.auth.impl;

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.SAMLEntryPoint;
import org.springframework.security.saml.metadata.MetadataDisplayFilter;
import org.springframework.security.saml.metadata.MetadataGeneratorFilter;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.SAMLFilterSet;

@Component
public class SAMLAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "saml";

	private static final String PROP_SUCCESS_LOGOUT_URL = "proxy.saml.logout-url";
	private static final String PROP_SAML_LOGOUT_METHOD = "proxy.saml.logout-method";

	@Autowired(required = false)
	private SAMLEntryPoint samlEntryPoint;
	
	@Autowired(required = false)
	private MetadataGeneratorFilter metadataGeneratorFilter;

	@Autowired(required = false)
	private MetadataDisplayFilter metadataDisplayFilter;

	@Autowired(required = false)
	private SAMLFilterSet samlFilter;
	
	@Autowired(required = false)
	private SAMLAuthenticationProvider samlAuthenticationProvider;

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
		http
			.exceptionHandling().authenticationEntryPoint(samlEntryPoint)
		.and()
			.addFilterBefore(metadataGeneratorFilter, ChannelProcessingFilter.class)
			.addFilterAfter(metadataDisplayFilter, MetadataGeneratorFilter.class)
			.addFilterAfter(samlFilter, BasicAuthenticationFilter.class);
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(samlAuthenticationProvider);
	}

	@Override
	public String getLogoutURL() {
		LogoutMethod logoutMethod = environment.getProperty(PROP_SAML_LOGOUT_METHOD, LogoutMethod.class, LogoutMethod.LOCAL);
		if (logoutMethod == LogoutMethod.LOCAL) {
			return "/logout";
		}
		return "/saml/logout"; // LogoutMethod.SAML
	}

	@Override
	public String getLogoutSuccessURL() {
	    return determineLogoutSuccessURL(environment);
	}

	public static String determineLogoutSuccessURL(Environment environment) {
		String logoutURL = environment.getProperty(PROP_SUCCESS_LOGOUT_URL);
		if (logoutURL == null || logoutURL.trim().isEmpty()) {
			logoutURL = "/";
		}
		return logoutURL;
	}

	private enum LogoutMethod {
		LOCAL,
		SAML
	}

}
