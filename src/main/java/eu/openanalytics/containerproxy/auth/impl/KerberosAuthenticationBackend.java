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

import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.springframework.security.kerberos.authentication.KerberosServiceRequestToken;
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosClient;
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter;
import org.springframework.security.kerberos.web.authentication.SpnegoEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.kerberos.KRBClientCacheRegistry;
import eu.openanalytics.containerproxy.auth.impl.kerberos.KRBServiceAuthProvider;
import eu.openanalytics.containerproxy.auth.impl.kerberos.KRBTicketValidator;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;

//TODO When user logs out: KRBClientCacheRegistry.remove(String principal)
public class KerberosAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "kerberos";

	private KRBClientCacheRegistry ccacheReg;
	
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
		ccacheReg = new KRBClientCacheRegistry(environment.getProperty("proxy.kerberos.client-ccache-path"));
		
		UserDetailsService uds = new SimpleUserDetailsService();

		KerberosAuthenticationProvider formAuthProvider = new KerberosAuthenticationProvider();
		SunJaasKerberosClient client = new SunJaasKerberosClient();
		client.setDebug(true);
		formAuthProvider.setKerberosClient(client);
		formAuthProvider.setUserDetailsService(uds);
		auth.authenticationProvider(formAuthProvider);

		KRBServiceAuthProvider spnegoAuthProvider = new KRBServiceAuthProvider(
				environment.getProperty("proxy.kerberos.backend-principal"),
				ccacheReg);
		KRBTicketValidator ticketValidator = new KRBTicketValidator(
				environment.getProperty("proxy.kerberos.service-principal"),
				new FileSystemResource(environment.getProperty("proxy.kerberos.service-keytab")));
		ticketValidator.init();
		spnegoAuthProvider.setTicketValidator(ticketValidator);
		spnegoAuthProvider.setUserDetailsService(uds);
		auth.authenticationProvider(spnegoAuthProvider);
	}

	@Override
	public void customizeContainer(ContainerSpec spec) {
		String principal = getCurrentPrincipal();
		String ccache = ccacheReg.get(principal);
		
		List<String> volumes = new ArrayList<>();
		if (spec.getVolumes() != null) {
			for (int i = 0; i < spec.getVolumes().length; i++) {
				volumes.add(spec.getVolumes()[i]);
			}
		}
		
		String ccachePath = Paths.get(ccache).getParent().toString();
		volumes.add(ccachePath + ":/tmp/kerberos");
		
		spec.setVolumes(volumes.toArray(new String[volumes.size()]));
	}
	
	@Override
	public void customizeContainerEnv(List<String> env) {
		String principal = getCurrentPrincipal();
		env.add("REMOTE_USER=" + principal);
		env.add("KRB5CCNAME=/tmp/kerberos/ccache");
	}
	
	private String getCurrentPrincipal() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof KerberosServiceRequestToken) {
			return auth.getName();
		}
		return null;
	}
	
	private static class SimpleUserDetailsService implements UserDetailsService {
		@Override
		public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
			return new User(username, "", Collections.emptyList());
		}
	}

}
