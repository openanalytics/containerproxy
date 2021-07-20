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

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.security.FixedDefaultOAuth2AuthorizationRequestResolver;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.util.SessionHelper;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class OpenIDAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "openid";

	private static final String REG_ID = "shinyproxy";
	private static final String ENV_TOKEN_NAME = "SHINYPROXY_OIDC_ACCESS_TOKEN";
	
	private Logger log = LogManager.getLogger(OpenIDAuthenticationBackend.class);
	
	private static OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

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
		ClientRegistrationRepository clientRegistrationRepo = createClientRepo();
		oAuth2AuthorizedClientRepository = new HttpSessionOAuth2AuthorizedClientRepository();

		anyRequestConfigurer.authenticated();
		
		http
			.oauth2Login()
				.loginPage("/login")
				.clientRegistrationRepository(clientRegistrationRepo)
				.authorizedClientRepository(oAuth2AuthorizedClientRepository)
				.authorizationEndpoint()
					.authorizationRequestResolver(new FixedDefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepo, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI))
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
					.oidcUserService(createOidcUserService());
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		// Nothing to do.
	}

	public String getLoginRedirectURI() {
		return SessionHelper.getContextPath(environment, false) 
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
	public void customizeContainerEnv(Map<String, String> env) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) return;

		OidcUser user = (OidcUser) auth.getPrincipal();
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
		OAuth2AuthorizedClient client = oAuth2AuthorizedClientRepository.loadAuthorizedClient(REG_ID, auth, request);
		if (client == null || client.getAccessToken() == null) return;

		env.put(ENV_TOKEN_NAME, client.getAccessToken().getTokenValue());
	}

	@Inject
	private SpecExpressionResolver specExpressionResolver;

	@Override
	public LogoutSuccessHandler getLogoutSuccessHandler() {
		return (httpServletRequest, httpServletResponse, authentication) -> {
			SpecExpressionContext context = SpecExpressionContext.create(authentication.getPrincipal(), authentication.getCredentials());
			String resolvedLogoutUrl = specExpressionResolver.evaluateToString(getLogoutSuccessURL(), context);

			SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();
			delegate.setDefaultTargetUrl(resolvedLogoutUrl);
			delegate.onLogoutSuccess(httpServletRequest, httpServletResponse, authentication);
		};
	}
	
	protected ClientRegistrationRepository createClientRepo() {
		Set<String> scopes = new HashSet<>();
		scopes.add("openid");
		scopes.add("email");
		
		for (int i=0;;i++) {
			String scope = environment.getProperty(String.format("proxy.openid.scopes[%d]", i));
			if (scope == null) break;
			else scopes.add(scope);
		}
		
		ClientRegistration client = ClientRegistration.withRegistrationId(REG_ID)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.clientName(REG_ID)
				.redirectUriTemplate("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope(scopes.toArray(new String[scopes.size()]))
				.userNameAttributeName(environment.getProperty("proxy.openid.username-attribute", "email"))
				.authorizationUri(environment.getProperty("proxy.openid.auth-url"))
				.tokenUri(environment.getProperty("proxy.openid.token-url"))
				.jwkSetUri(environment.getProperty("proxy.openid.jwks-url"))
				.clientId(environment.getProperty("proxy.openid.client-id"))
				.clientSecret(environment.getProperty("proxy.openid.client-secret"))
				.build();
		
		return new InMemoryClientRegistrationRepository(Collections.singletonList(client));
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
					throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST), "Error while loading user info", ex);
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
				else return emails.toString();
			}
			else return super.getName();
		}

		public String getRefreshToken() {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
			OAuth2AuthorizedClient client = oAuth2AuthorizedClientRepository.loadAuthorizedClient(REG_ID, auth, request);

			if (client != null) {
				OAuth2RefreshToken refreshToken = client.getRefreshToken();
				if (refreshToken != null) {
					return refreshToken.getTokenValue();
				}
			}
			return null;
		}
	}
}
