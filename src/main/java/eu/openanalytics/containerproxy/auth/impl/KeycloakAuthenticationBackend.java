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

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.keycloak.AuthenticationFaillureHandler;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.spi.HttpFacade.Request;
import org.keycloak.adapters.spi.KeycloakAccount;
import org.keycloak.adapters.springsecurity.AdapterDeploymentContextFactoryBean;
import org.keycloak.adapters.springsecurity.account.KeycloakRole;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationEntryPoint;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationFailureHandler;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationProvider;
import org.keycloak.adapters.springsecurity.authentication.KeycloakLogoutHandler;
import org.keycloak.adapters.springsecurity.filter.KeycloakAuthenticationProcessingFilter;
import org.keycloak.adapters.springsecurity.filter.KeycloakPreAuthActionsFilter;
import org.keycloak.adapters.springsecurity.management.HttpSessionManager;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.servlet.ServletException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KeycloakAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "keycloak";
	
	@Inject
	Environment environment;

	@Inject
	ApplicationContext ctx;

	@Inject
	@Lazy
	AuthenticationManager authenticationManager;

	@Inject
	@Lazy
	private SavedRequestAwareAuthenticationSuccessHandler successHandler;

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
		http.formLogin().disable();
		
		http
			.sessionManagement().sessionAuthenticationStrategy(sessionAuthenticationStrategy())
			.and()
			.addFilterBefore(keycloakPreAuthActionsFilter(), LogoutFilter.class)
			.addFilterBefore(keycloakAuthenticationProcessingFilter(), BasicAuthenticationFilter.class)
			.exceptionHandling().authenticationEntryPoint(authenticationEntryPoint())
			.and()
			.logout().addLogoutHandler(keycloakLogoutHandler());
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		 auth.authenticationProvider(keycloakAuthenticationProvider());
	}

	@Override
	public String getLogoutSuccessURL() {
		return "/";
	}
	
	@Bean
	@ConditionalOnProperty(name="proxy.authentication", havingValue="keycloak")
	protected KeycloakAuthenticationProcessingFilter keycloakAuthenticationProcessingFilter() throws Exception {
		// Possible solution for issue #21037, create a custom RequestMatcher that doesn't include a QueryParamPresenceRequestMatcher(OAuth2Constants.ACCESS_TOKEN) request matcher.
		// The QueryParamPresenceRequestMatcher(OAuth2Constants.ACCESS_TOKEN) caused the HTTP requests to be changed before they where processed.
		// Because the HTTP requests are adapted before they are processed, the requested failed to complete successfully and caused an io.undertow.server.TruncatedResponseException
		// If in the future we need a RequestMatcher for het ACCESS_TOKEN, we can implement one ourself
		RequestMatcher requestMatcher =
				new OrRequestMatcher(
	                    new AntPathRequestMatcher("/sso/login"),
						new RequestHeaderRequestMatcher(KeycloakAuthenticationProcessingFilter.AUTHORIZATION_HEADER)
	            );

		KeycloakAuthenticationProcessingFilter filter = new KeycloakAuthenticationProcessingFilter(authenticationManager, requestMatcher);
		filter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy());
		filter.setAuthenticationFailureHandler(keycloakAuthenticationFailureHandler());
		filter.setAuthenticationSuccessHandler(successHandler);
		// Fix: call afterPropertiesSet manually, because Spring doesn't invoke it for some reason.
		filter.setApplicationContext(ctx);
		filter.afterPropertiesSet();
		return filter;
	}

	@Bean
	@ConditionalOnProperty(name="proxy.authentication", havingValue="keycloak")
	protected KeycloakPreAuthActionsFilter keycloakPreAuthActionsFilter() {
		KeycloakPreAuthActionsFilter filter = new KeycloakPreAuthActionsFilter(httpSessionManager());
		// Fix: call afterPropertiesSet manually, because Spring doesn't invoke it for some reason.
		filter.setApplicationContext(ctx);
		try { filter.afterPropertiesSet(); } catch (ServletException e) {}
		return filter;
	}

	@Bean
	@ConditionalOnProperty(name="proxy.authentication", havingValue="keycloak")
	protected HttpSessionManager httpSessionManager() {
		return new HttpSessionManager();
	}

	@Bean
	@ConditionalOnProperty(name="proxy.authentication", havingValue="keycloak")
	protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new CompositeSessionAuthenticationStrategy(Arrays.asList(
			new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl()),
			new ChangeSessionIdAuthenticationStrategy()
		));
	}

	@Bean
	@ConditionalOnProperty(name="proxy.authentication", havingValue="keycloak")
	public KeycloakAuthenticationFailureHandler keycloakAuthenticationFailureHandler() {
		return new AuthenticationFaillureHandler();
	}

	@Bean
	@ConditionalOnProperty(name="proxy.authentication", havingValue="keycloak")
	protected AdapterDeploymentContext adapterDeploymentContext() throws Exception {
		AdapterConfig cfg = new AdapterConfig();
		cfg.setRealm(environment.getProperty("proxy.keycloak.realm"));
		cfg.setAuthServerUrl(environment.getProperty("proxy.keycloak.auth-server-url"));
		cfg.setResource(environment.getProperty("proxy.keycloak.resource"));
		cfg.setSslRequired(environment.getProperty("proxy.keycloak.ssl-required", "external"));
		cfg.setUseResourceRoleMappings(Boolean.valueOf(environment.getProperty("proxy.keycloak.use-resource-role-mappings", "false")));
		Map<String,Object> credentials = new HashMap<>();
		credentials.put("secret", environment.getProperty("proxy.keycloak.credentials-secret"));
		cfg.setCredentials(credentials);
		KeycloakDeployment dep = KeycloakDeploymentBuilder.build(cfg);
		AdapterDeploymentContextFactoryBean factoryBean = new AdapterDeploymentContextFactoryBean(new KeycloakConfigResolver() {
			@Override
			public KeycloakDeployment resolve(Request facade) {
				return dep;
			}
		});
		factoryBean.afterPropertiesSet();
		return factoryBean.getObject();
	}

	protected AuthenticationEntryPoint authenticationEntryPoint() throws Exception {
        return new KeycloakAuthenticationEntryPoint(adapterDeploymentContext());
    }
	
	protected KeycloakAuthenticationProvider keycloakAuthenticationProvider() {
		return new KeycloakAuthenticationProvider() {
			@Override
			public Authentication authenticate(Authentication authentication) throws AuthenticationException {
				KeycloakAuthenticationToken token = (KeycloakAuthenticationToken) super.authenticate(authentication);
				List<GrantedAuthority> auth = token.getAuthorities().stream()
						.map(t -> t.getAuthority().toUpperCase())
						.map(a -> a.startsWith("ROLE_") ? a : "ROLE_" + a)
						.map(a -> new KeycloakRole(a))
						.collect(Collectors.toList());
				String nameAttribute = environment.getProperty("proxy.keycloak.name-attribute", IDToken.NAME).toLowerCase();
				return new KeycloakAuthenticationToken2(token.getAccount(), token.isInteractive(), nameAttribute, auth);
			}
		};
	}
	
	protected KeycloakLogoutHandler keycloakLogoutHandler() throws Exception {
		return new KeycloakLogoutHandler(adapterDeploymentContext());
	}
	
	public static class KeycloakAuthenticationToken2 extends KeycloakAuthenticationToken implements Serializable {
		
		private static final long serialVersionUID = -521347733024996150L;

		private String nameAttribute;
		
		public KeycloakAuthenticationToken2(KeycloakAccount account, boolean interactive, String nameAttribute, Collection<? extends GrantedAuthority> authorities) {
			super(account, interactive, authorities);
			this.nameAttribute = nameAttribute;
		}
		
		@Override
		public String getName() {
			IDToken token = getAccount().getKeycloakSecurityContext().getIdToken();
			if (token == null) {
				// when ContainerProxy is accessed directly using the Access Token as Bearer value in the Authorization
				// header, no ID Token is present. The AccessTokens provided by Keycloak are in fact ID tokens, so we
				// can safely fall back to them.
				token = getAccount().getKeycloakSecurityContext().getToken();
			}
			switch (nameAttribute) {
			case IDToken.PREFERRED_USERNAME: return token.getPreferredUsername();
			case IDToken.NICKNAME: return token.getNickName();
			case IDToken.EMAIL: return token.getEmail();
			default: return token.getName();
			}
		}
	}
}
