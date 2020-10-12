/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.kerberos.authentication.KerberosServiceAuthenticationProvider;
import org.springframework.security.kerberos.authentication.KerberosServiceRequestToken;
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosTicketValidator;
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter;
import org.springframework.security.kerberos.web.authentication.SpnegoEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.context.annotation.Lazy;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.kerberos.KRBClientCacheRegistry;
import eu.openanalytics.containerproxy.auth.impl.kerberos.KRBTicketRenewalManager;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.service.EventService;
import eu.openanalytics.containerproxy.service.EventService.EventType;

public class KerberosAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "kerberos";

	private KRBClientCacheRegistry ccacheReg;
	
	@Inject
	Environment environment;
	
	@Lazy
	@Inject
	AuthenticationManager authenticationManager;

	@Inject
	EventService eventService;
	
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

		SpnegoAuthenticationProcessingFilter filter = new SpnegoAuthenticationProcessingFilter();
		filter.setAuthenticationManager(authenticationManager);

		http
			.exceptionHandling().authenticationEntryPoint(new SpnegoEntryPoint("/login")).and()
			.addFilterBefore(filter, BasicAuthenticationFilter.class);
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		ccacheReg = new KRBClientCacheRegistry(environment.getProperty("proxy.kerberos.client-ccache-path"));
		
		String authSvcPrinc = environment.getProperty("proxy.kerberos.auth-service-principal", environment.getProperty("proxy.kerberos.service-principal"));
		String authSvcKeytab = environment.getProperty("proxy.kerberos.auth-service-keytab", environment.getProperty("proxy.kerberos.service-keytab"));
		String delegSvcPrinc = environment.getProperty("proxy.kerberos.deleg-service-principal", authSvcPrinc);
		String delegSvcKeytab = environment.getProperty("proxy.kerberos.deleg-service-keytab", authSvcKeytab);
		
		List<String> backendPrincipalList = new ArrayList<>();
		String backendPrincipal = environment.getProperty("proxy.kerberos.backend-principal", (String) null);
		if (backendPrincipal != null) backendPrincipalList.add(backendPrincipal);
		String[] moreBackendPrincipals = environment.getProperty("proxy.kerberos.backend-principals", String[].class, new String[0]);
		for (String p: moreBackendPrincipals) backendPrincipalList.add(p);
		String[] backendPrincipals = backendPrincipalList.stream().toArray(i -> new String[i]);
		
		long ticketRenewInterval = environment.getProperty("proxy.kerberos.ticket-renew-interval", Long.class, new Long(8 * 3600 * 1000));
		
		SunJaasKerberosTicketValidator ticketValidator = new SunJaasKerberosTicketValidator();
		ticketValidator.setServicePrincipal(authSvcPrinc);
		ticketValidator.setKeyTabLocation(new FileSystemResource(authSvcKeytab));
		ticketValidator.setDebug(true);
		ticketValidator.afterPropertiesSet();
		
		KRBTicketRenewalManager renewalManager = new KRBTicketRenewalManager(
				delegSvcPrinc, delegSvcKeytab, backendPrincipals, ccacheReg, ticketRenewInterval);
		
		eventService.addListener(e -> {
			if (EventType.Logout.toString().equals(e.type)) renewalManager.stop(e.user);
		});
		
		KerberosServiceAuthenticationProvider authProvider = new KerberosServiceAuthenticationProvider() {
			@Override
			public Authentication authenticate(Authentication authentication) throws AuthenticationException {
				KerberosServiceRequestToken auth = (KerberosServiceRequestToken) super.authenticate(authentication);
				renewalManager.start(auth.getName());
				return auth;
			}
		};
		authProvider.setTicketValidator(ticketValidator);
		authProvider.setUserDetailsService(new SimpleUserDetailsService());
		authProvider.afterPropertiesSet();
		
		auth.authenticationProvider(authProvider);
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
		env.add("KRB5CCNAME=FILE:/tmp/kerberos/ccache");
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
