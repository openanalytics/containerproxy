package eu.openanalytics.containerproxy.auth.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;

/**
 * See https://docs.spring.io/spring-security/site/docs/current/reference/html/oauth2login-advanced.html
 * 
 * Notes on Auth0 tests:
 * - Callback URL: http://localhost:8080/login/oauth2/code/shinyproxy
 * - Added app_metadata to user: "shinyproxy_roles": [ "scientists", "mathematicians" ]
 * - Added rule to attach "shinyproxy_roles" to the ID token during authentication:
 * function (user, context, callback) {
 *   context.idToken['https://shinyproxy.io/shinyproxy_roles'] = user.app_metadata.shinyproxy_roles;
 *   callback(null, user, context);
 * }
 * 
 */
public class OpenIDAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "openid";

	private static final String REG_ID = "shinyproxy";
	
	@Inject
	Environment environment;
	
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
		http
			.authorizeRequests().anyRequest().authenticated()
			.and()
			.oauth2Login()
				.loginPage("/login")
				.clientRegistrationRepository(createClientRepo())
				.userInfoEndpoint().userAuthoritiesMapper(createAuthoritiesMapper());
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		// Nothing to do.
	}

	public String getLoginRedirectURI() {
		return OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI + "/" + REG_ID;
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
				.userNameAttributeName(environment.getProperty("username-attribute", "email"))
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
						List<String> roles = idToken.getClaimAsStringList(rolesClaimName);
						for (String role: roles) {
							String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
							mappedAuthorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
						}
					}
				}
				return mappedAuthorities;
			};
		}
	}
}
