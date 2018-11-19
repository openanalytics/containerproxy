/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.kerberos.authentication.KerberosAuthenticationProvider;
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosClient;
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter;
import org.springframework.security.kerberos.web.authentication.SpnegoEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.kerberos.KRBAuthenticationToken;
import eu.openanalytics.containerproxy.auth.impl.kerberos.KRBServiceAuthProvider;
import eu.openanalytics.containerproxy.auth.impl.kerberos.KRBTicketValidator;

public class KerberosAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "kerberos";

	@Inject
	Environment environment;
	
	@Inject
	AuthenticationManager authenticationManager;

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

		SpnegoAuthenticationProcessingFilter filter = new SpnegoAuthenticationProcessingFilter();
		filter.setAuthenticationManager(authenticationManager);

		http
			.exceptionHandling().authenticationEntryPoint(new SpnegoEntryPoint("/login")).and()
			.addFilterBefore(filter, BasicAuthenticationFilter.class);
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		UserDetailsService uds = new SimpleUserDetailsService();

		KerberosAuthenticationProvider formAuthProvider = new KerberosAuthenticationProvider();
		SunJaasKerberosClient client = new SunJaasKerberosClient();
		client.setDebug(true);
		formAuthProvider.setKerberosClient(client);
		formAuthProvider.setUserDetailsService(uds);
		auth.authenticationProvider(formAuthProvider);

		KRBServiceAuthProvider spnegoAuthProvider = new KRBServiceAuthProvider(
				environment.getProperty("proxy.kerberos.backend-principal"),
				environment.getProperty("proxy.kerberos.client-ccache-path"));
		KRBTicketValidator ticketValidator = new KRBTicketValidator(
				environment.getProperty("proxy.kerberos.service-principal"),
				new FileSystemResource(environment.getProperty("proxy.kerberos.service-keytab")));
		ticketValidator.init();
		spnegoAuthProvider.setTicketValidator(ticketValidator);
		spnegoAuthProvider.setUserDetailsService(uds);
		auth.authenticationProvider(spnegoAuthProvider);
	}

	@Override
	public void customizeContainerEnv(List<String> env) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof KRBAuthenticationToken) {
			KRBAuthenticationToken token = (KRBAuthenticationToken) auth;
			env.add("KRB5CCNAME=" + token.getClientCCPath());
			env.add("REMOTE_USER=" + token.getClientName());
		}
	}
	
	private static class SimpleUserDetailsService implements UserDetailsService {
		@Override
		public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
			return new User(username, "", Collections.emptyList());
		}
	}

}
