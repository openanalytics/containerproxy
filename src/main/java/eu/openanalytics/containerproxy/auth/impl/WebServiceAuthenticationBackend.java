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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;

/**
 * Web service authentication method where user/password combinations are
 * checked by a HTTP call to a remote web service.
 */
public class WebServiceAuthenticationBackend implements IAuthenticationBackend {
	
	private final class WebServicePrincipal implements AuthenticatedPrincipal {
		
		private final String username;
		private final String token;

		private WebServicePrincipal(String username, String token) {
			super();
			this.username = username;
			this.token = token;
		}

		public String getToken() {
			return token;
		}

		@Override
		public String getName() {
			return username;
		}

		@Override
		public String toString() {
			return getName();
		}

	}

	public static final String NAME = "webservice";
	private static final String ENV_TOKEN = "SHINYPROXY_WEBSERVICE_ACCESS_TOKEN";

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
	public void configureHttpSecurity(HttpSecurity http) throws Exception {
		http.logout().addLogoutHandler(new LogoutHandler() {
			
			@Override
			public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
				String logoutUrl = environment.getProperty("proxy.authentication-logout-url");
				RestTemplate restTemplate = new RestTemplate();
				HttpHeaders headers = new HttpHeaders();
				headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
				
				WebServicePrincipal user = (WebServicePrincipal) authentication.getPrincipal();
				String tokenHeader = environment.getProperty("proxy.authentication-logout-token-header");
				String tokenValue = String.format(environment.getProperty("proxy.authentication-logout-token-value", "%s"), user.getToken());
				headers.add(tokenHeader, tokenValue);
				
				restTemplate.exchange(logoutUrl, HttpMethod.POST, new HttpEntity<>(headers), String.class);
			}
		});
	}
		
	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		AuthenticationProvider authenticationProvider = new AuthenticationProvider() {
			
			@Override
			public Authentication authenticate(Authentication authentication) throws AuthenticationException {
				String username = authentication.getPrincipal().toString();
				Object credentials = authentication.getCredentials();
				String password = credentials == null ? null : credentials.toString();

				RestTemplate restTemplate = new RestTemplate();

				HttpHeaders headers = new HttpHeaders();
				headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
				headers.setContentType(MediaType.APPLICATION_JSON);

				try {
					String body = String.format(environment.getProperty("proxy.authentication-request-body", ""), username, password);
					String loginUrl = environment.getProperty("proxy.authentication-url");
					ResponseEntity<String> result = restTemplate.exchange(loginUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
					if (result.getStatusCode() == HttpStatus.OK) {
						
						String token = null;
						String tokenJsonPath = environment.getProperty("proxy.authentication-response-token");
						if (tokenJsonPath != null) {
							token = JsonPath.parse(result.getBody()).read(tokenJsonPath);	
						}

						Set<GrantedAuthority> authorities = new HashSet<>();
						String roleJsonPath = environment.getProperty("proxy.authentication-response-roles");
						if (roleJsonPath != null) {
							ParseContext roleParser = JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.ALWAYS_RETURN_LIST));
							List<String> roles = roleParser.parse(result.getBody()).read(roleJsonPath);
							for (String role: roles) {
								String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
								authorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
							}	
						}
						
						return new UsernamePasswordAuthenticationToken(new WebServicePrincipal(username, token), password, authorities);
					}
					throw new AuthenticationServiceException("Unknown response received " + result);
					
				} catch (HttpClientErrorException e) {
					throw new BadCredentialsException("Invalid username or password");
				} catch (RestClientException e) {
					throw new AuthenticationServiceException("Internal error " + e.getMessage());
				}
			}

			@Override
			public boolean supports(Class<?> authentication) {
				return (UsernamePasswordAuthenticationToken.class
						.isAssignableFrom(authentication));
			}
						
		};
		auth.authenticationProvider(authenticationProvider);
	}

	@Override
	public void customizeContainerEnv(List<String> env) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) return;

		WebServicePrincipal user = (WebServicePrincipal) auth.getPrincipal();
		env.add(ENV_TOKEN + "=" + user.getToken());
	}
	
}