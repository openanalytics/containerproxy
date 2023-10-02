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
import eu.openanalytics.containerproxy.auth.impl.oidc.OpenIdReAuthorizeFilter;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.util.ContextPathHelper;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.openanalytics.containerproxy.auth.impl.oidc.OpenIDConfiguration.REG_ID;

public class OpenIDAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "openid";

	private static final String ENV_TOKEN_NAME = "SHINYPROXY_OIDC_ACCESS_TOKEN";
	
	private final Logger log = LogManager.getLogger(OpenIDAuthenticationBackend.class);
	
	@Inject
	private Environment environment;

	@Inject
	private ClientRegistrationRepository clientRegistrationRepo;

	@Inject
	@Lazy
	private SavedRequestAwareAuthenticationSuccessHandler successHandler;

	private static OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

	@Autowired
	public void setOAuth2AuthorizedClientService(OAuth2AuthorizedClientService oAuth2AuthorizedClientService)  {
		OpenIDAuthenticationBackend.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
	}

	@Inject
	private OpenIdReAuthorizeFilter openIdReAuthorizeFilter;

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
		anyRequestConfigurer.authenticated();

		http
			.oauth2Login()
				.loginPage("/login")
				.successHandler(successHandler)
				.clientRegistrationRepository(clientRegistrationRepo)
				.authorizedClientService(oAuth2AuthorizedClientService)
				.authorizationEndpoint()
					.authorizationRequestResolver(authorizationRequestResolver())
				.and()
				.failureHandler(new AuthenticationFailureHandler() {

					@Override
					public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
							AuthenticationException exception) throws IOException, ServletException {
						log.error(exception);
						response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/auth-error").build().toUriString());
					}
					
				})
				.userInfoEndpoint()
					.userAuthoritiesMapper(createAuthoritiesMapper())
					.oidcUserService(createOidcUserService())
				.and()
			.and()
			.addFilterAfter(openIdReAuthorizeFilter, UsernamePasswordAuthenticationFilter.class);
	}

	private OAuth2AuthorizationRequestResolver authorizationRequestResolver() {
		Boolean usePkce = environment.getProperty("proxy.openid.with-pkce", Boolean.class, false);
		DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepo,
				OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);

		if (usePkce) {
			authorizationRequestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
		}

		return authorizationRequestResolver;
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		// Nothing to do.
	}

	public String getLoginRedirectURI() {
		return ContextPathHelper.withoutEndingSlash()
				+ OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI 
				+ "/" + REG_ID;
	}
	
	@Override
	public String getLogoutSuccessURL() {
		String logoutURL = environment.getProperty("proxy.openid.logout-url");
		if (logoutURL == null || logoutURL.trim().isEmpty()) logoutURL = IAuthenticationBackend.super.getLogoutSuccessURL();
		return logoutURL;
	}
	
	@Override
	public void customizeContainerEnv(Authentication user, Map<String, String> env) {
		OAuth2AuthorizedClient client = refreshClient(user.getName());
		if (client == null || client.getAccessToken() == null) return;
		env.put(ENV_TOKEN_NAME, client.getAccessToken().getTokenValue());
	}

	@Inject
	private SpecExpressionResolver specExpressionResolver;

	@Override
	public LogoutSuccessHandler getLogoutSuccessHandler() {
		return (httpServletRequest, httpServletResponse, authentication) -> {
			String resolvedLogoutUrl;
			if (authentication != null) {
				SpecExpressionContext context = SpecExpressionContext.create(authentication.getPrincipal(), authentication.getCredentials());
				resolvedLogoutUrl = specExpressionResolver.evaluateToString(getLogoutSuccessURL(), context);
			} else {
				resolvedLogoutUrl = getLogoutSuccessURL();
			}

			SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();
			delegate.setDefaultTargetUrl(resolvedLogoutUrl);
			delegate.onLogoutSuccess(httpServletRequest, httpServletResponse, authentication);
		};
	}
	
	protected GrantedAuthoritiesMapper createAuthoritiesMapper() {
		String rolesClaimName = environment.getProperty("proxy.openid.roles-claim");
		if (rolesClaimName == null || rolesClaimName.isEmpty()) {
			return authorities -> authorities;
		} else {
			return authorities -> {
				Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
				for (GrantedAuthority auth: authorities) {
					if (auth instanceof OidcUserAuthority) {
						OidcIdToken idToken = ((OidcUserAuthority) auth).getIdToken();
						
						if (log.isDebugEnabled()) {
							String lineSep = System.getProperty("line.separator");
							String claims = idToken.getClaims().entrySet().stream()
								.map(e -> String.format("%s -> %s", e.getKey(), e.getValue()))
								.collect(Collectors.joining(lineSep));
							log.debug(String.format("Checking for roles in claim '%s'. Available claims in ID token (%d):%s%s",
									rolesClaimName, idToken.getClaims().size(), lineSep, claims));
						}
						
						Object claimValue = idToken.getClaims().get(rolesClaimName);

						for (String role: parseRolesClaim(log, rolesClaimName, claimValue)) {
							String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
							mappedAuthorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
						}
					}
				}
				return mappedAuthorities;
			};
		}
	}

	/**
	 * Parses the claim containing the roles to a List of Strings.
	 * See #25549 and TestOpenIdParseClaimRoles
	 */
	public static List<String> parseRolesClaim(Logger log, String rolesClaimName, Object claimValue) {
		if (claimValue == null) {
			log.debug(String.format("No roles claim with name %s found", rolesClaimName));
			return new ArrayList<>();
		} else {
			log.debug(String.format("Matching claim found: %s -> %s (%s)", rolesClaimName, claimValue, claimValue.getClass()));
		}

		if (claimValue instanceof Collection) {
			List<String> result = new ArrayList<>();
			for (Object object : ((Collection<?>) claimValue)) {
				if (object != null) {
					result.add(object.toString());
				}
			}
			log.debug(String.format("Parsed roles claim as Java Collection: %s -> %s (%s)", rolesClaimName, result, result.getClass()));
			return result;
		}

		if (claimValue instanceof String) {
			List<String> result = new ArrayList<>();
			try {
				Object value = new JSONParser(JSONParser.MODE_PERMISSIVE).parse((String) claimValue);
				if (value instanceof List) {
					List<?> valueList = (List<?>) value;
					valueList.forEach(o -> result.add(o.toString()));
				}
			} catch (ParseException e) {
				// Unable to parse JSON
				log.debug(String.format("Unable to parse claim as JSON: %s -> %s (%s)", rolesClaimName, claimValue, claimValue.getClass()));
			}
			log.debug(String.format("Parsed roles claim as JSON: %s -> %s (%s)", rolesClaimName, result, result.getClass()));
			return result;
		}

		log.debug(String.format("No parser found for roles claim (unsupported type): %s -> %s (%s)", rolesClaimName, claimValue, claimValue.getClass()));
		return new ArrayList<>();
	}

	protected OidcUserService createOidcUserService() {
		// Use a custom UserService that supports the 'emails' array attribute.
		return new OidcUserService() {
			@Override
			public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
			    OidcUser user;
				try {
					user = super.loadUser(userRequest);
				} catch (IllegalArgumentException ex) {
					log.warn("Error while loading user info: {}", ex.getMessage());
					throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST), "Error while loading user info", ex);
				} catch (OAuth2AuthenticationException ex) {
					log.warn("Error while loading user info: {}", ex.getMessage());
					throw ex;
				}

				String nameAttributeKey = environment.getProperty("proxy.openid.username-attribute", "email");
				return new CustomNameOidcUser(new HashSet<>(user.getAuthorities()),
						user.getIdToken(),
						user.getUserInfo(),
						nameAttributeKey
				);
			}
		};
	}

	private static OAuth2AuthorizedClient refreshClient(String principalName) {
		return oAuth2AuthorizedClientService.loadAuthorizedClient(REG_ID, principalName);
	}


	public static class CustomNameOidcUser extends DefaultOidcUser {

		private static final long serialVersionUID = 7563253562760236634L;
		private static final String ID_ATTR_EMAILS = "emails";
		
		private final boolean isEmailsAttribute;

		public CustomNameOidcUser(Set<GrantedAuthority> authorities, OidcIdToken idToken, OidcUserInfo userInfo, String nameAttributeKey) {
			super(authorities, idToken, userInfo, nameAttributeKey);
			this.isEmailsAttribute = nameAttributeKey.equals(ID_ATTR_EMAILS);
		}

		@Override
		public String getName() {
			if (isEmailsAttribute) {
				Object emails = getAttributes().get(ID_ATTR_EMAILS);
				if (emails instanceof String[]) return ((String[]) emails)[0];
				else if (emails instanceof JSONArray) return ((JSONArray) emails).get(0).toString();
				else if (emails instanceof Collection) return ((Collection<?>) emails).stream().findFirst().get().toString();
				else return emails.toString();
			}
			else return super.getName();
		}

		public String getRefreshToken() {
			OAuth2AuthorizedClient client = refreshClient(getName());
			if (client == null || client.getRefreshToken() == null) {
				return null;
			}
			return client.getRefreshToken().getTokenValue();
		}
	}
}
