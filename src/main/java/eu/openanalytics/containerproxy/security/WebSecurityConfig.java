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
package eu.openanalytics.containerproxy.security;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.UserLogoutHandler;
import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.util.AppRecoveryFilter;
import eu.openanalytics.containerproxy.util.EnvironmentUtils;
import eu.openanalytics.containerproxy.util.OverridingHeaderWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.header.Header;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static eu.openanalytics.containerproxy.ui.AuthController.AUTH_SUCCESS_URL;
import static eu.openanalytics.containerproxy.ui.TemplateResolverConfig.PROP_CORS_ALLOWED_ORIGINS;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	private final Logger logger = LogManager.getLogger(getClass());

	@Inject
	private UserLogoutHandler logoutHandler;

	@Inject
	private IAuthenticationBackend auth;

	@Inject
	private Environment environment;

	@Inject
	private AppRecoveryFilter appRecoveryFilter;

	@Inject
	private IdentifierService identifierService;
	
	@Autowired(required=false)
	private List<ICustomSecurityConfig> customConfigs;

	@Inject
	@Lazy
	private SavedRequestAwareAuthenticationSuccessHandler successHandler;

	public static final String PROP_DISABLE_NO_SNIFF_HEADER = "proxy.api-security.disable-no-sniff-header";
	public static final String PROP_DISABLE_HSTS_HEADER = "proxy.api-security.disable-hsts-header";
	public static final String PROP_DISABLE_XSS_PROTECTION_HEADER = "proxy.api-security.disable-xss-protection-header";
	public static final String PROP_CUSTOM_HEADERS = "proxy.api-security.custom-headers";
	public static final String PROP_OAUTH2_RESOURCE_ID = "proxy.oauth2.resource-id";
	public static final String PROP_OAUTH2_JWKS_URL = "proxy.oauth2.jwks-url";
	public static final String PROP_OAUTH2_ROLES_CLAIM = "proxy.oauth2.roles-claim";
	public static final String PROP_OAUTH2_USERNAME_ATTRIBUTE = "proxy.oauth2.username-attribute";

	@Override
	public void configure(WebSecurity web) {
		if (customConfigs != null) {
			for (ICustomSecurityConfig cfg: customConfigs) {
				try {
					cfg.apply(web);
				} catch (Exception e) {
					// This function may not throw exceptions, therefore we exit the process here
					// We do not want half-configured security.
					e.printStackTrace();
					System.exit(1);
				}
			}
		}
	}

	private void checkForIncorrectConfiguration(HttpServletRequest request) {
		if (request.getScheme().equals("http") && ContainerProxyApplication.secureCookiesEnabled) {
			logger.warn("WARNING: Invalid configuration detected: ShinyProxy is accessed over HTTP but secure-cookies is enabled. Secure-cookies only work when accessing ShinyProxy over HTTPS. "
					+ "Ensure that ShinyProxy is accessed over HTTPS or disable secure-cookies");
		}
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		if (EnvironmentUtils.readList(environment, PROP_CORS_ALLOWED_ORIGINS) != null) {
			// enable cors
			http.cors();
		}

		// App Recovery Filter
		http.addFilterAfter(appRecoveryFilter, BasicAuthenticationFilter.class);

		// Perform CSRF check on the login form
		http.csrf().requireCsrfProtectionMatcher(new AntPathRequestMatcher("/login", "POST"));

		http.exceptionHandling().accessDeniedHandler(new AccessDeniedHandler() {
		    final AntPathRequestMatcher matcher = new AntPathRequestMatcher("/login", "POST");
		    final AccessDeniedHandler defaultAccessDeniedHandler = new AccessDeniedHandlerImpl();
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
				checkForIncorrectConfiguration(request);

				if (matcher.matcher(request).isMatch() && accessDeniedException instanceof MissingCsrfTokenException) {
					response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/login").queryParam("error", "expired").build().toUriString());
				} else {
					defaultAccessDeniedHandler.handle(request, response, accessDeniedException);
				}
			}
		});

		if (environment.getProperty(PROP_DISABLE_NO_SNIFF_HEADER, Boolean.class, false)) {
			http.headers().contentTypeOptions().disable();
		} else {
			// set header: X-Content-Type-Options=nosniff
			http.headers().contentTypeOptions();
		}

		if (environment.getProperty(PROP_DISABLE_XSS_PROTECTION_HEADER, Boolean.class, false)) {
			http.headers().xssProtection().disable();
		} else {
			http.headers().xssProtection();
		}

		if (environment.getProperty(PROP_DISABLE_HSTS_HEADER, Boolean.class, false)) {
			http.headers().httpStrictTransportSecurity().disable();
		} else {
			http.headers().httpStrictTransportSecurity();
		}


		String frameOptions = environment.getProperty("server.frame-options", "disable");
		switch (frameOptions.toUpperCase()) {
			case "DISABLE":
				http.headers().frameOptions().disable();
				break;
			case "DENY":
				http.headers().frameOptions().deny();
				break;
			case "SAMEORIGIN":
				http.headers().frameOptions().sameOrigin();
				break;
			default:
				if (frameOptions.toUpperCase().startsWith("ALLOW-FROM")) {
					http.headers()
						.frameOptions().disable()
						.addHeaderWriter(new StaticHeadersWriter("X-Frame-Options", frameOptions));
				}
		}

		List<Header> headers = getCustomHeaders();
		if (!headers.isEmpty()) {
			http.headers().addHeaderWriter(new OverridingHeaderWriter(headers));
		}

		// Allow public access to health endpoint
		http.authorizeRequests().antMatchers("/actuator/health").permitAll();
		http.authorizeRequests().antMatchers("/actuator/health/readiness").permitAll();
		http.authorizeRequests().antMatchers("/actuator/health/liveness").permitAll();
		http.authorizeRequests().antMatchers("/actuator/prometheus").permitAll();
		http.authorizeRequests().antMatchers("/actuator/recyclable").permitAll();
		http.authorizeRequests().antMatchers("/saml/metadata").permitAll();

		// Note: call early, before http.authorizeRequests().anyRequest().fullyAuthenticated();
		if (customConfigs != null) {
			for (ICustomSecurityConfig cfg: customConfigs) cfg.apply(http);
		}
		

		if (auth.hasAuthorization()) {
			http.authorizeRequests().antMatchers(
					"/login", "/signin/**", "/auth-error", "/error", "/app-access-denied", "/logout-success",
					"/favicon.ico",
					"/" + identifierService.instanceId + "/css/**", "/css/**",
					"/" + identifierService.instanceId + "/img/**", "/img/**",
					"/" + identifierService.instanceId + "/js/**", "/js/**",
					"/" + identifierService.instanceId + "/assets/**", "/assets/**",
					"/" + identifierService.instanceId + "/webjars/**", "/webjars/**"
					).permitAll();
			http
				.formLogin()
					.loginPage("/login")
					.successHandler(successHandler)
					.and()
				.logout()
					.logoutUrl(auth.getLogoutURL())
					// important: set the next option after logoutUrl because it would otherwise get overwritten
					.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
					.addLogoutHandler(logoutHandler)
					.logoutSuccessHandler(auth.getLogoutSuccessHandler());

			// Enable basic auth for RESTful calls when APISecurityConfig is not enabled.
			http.addFilter(new BasicAuthenticationFilter(super.authenticationManagerBean()));
		}
	

		if (auth.hasAuthorization()) {
			// The `anyRequest` method may only be called once.
			// Therefore we call it here, make our changes to it and forward it to the various authentication backends
			ExpressionUrlAuthorizationConfigurer<HttpSecurity>.AuthorizedUrl anyRequestConfigurer =  http.authorizeRequests().anyRequest();
			anyRequestConfigurer.fullyAuthenticated();
			auth.configureHttpSecurity(http, anyRequestConfigurer);
		}

		// create session cookie even if there is no Authentication in order to support the None authentication backend
		http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.ALWAYS);


		String oauth2JwksUri = environment.getProperty(PROP_OAUTH2_JWKS_URL);
		String resourceId = environment.getProperty(PROP_OAUTH2_RESOURCE_ID);
		if (oauth2JwksUri != null && resourceId != null) {
			http.oauth2ResourceServer()
					.jwt()
					.decoder(jwtDecoder(oauth2JwksUri, resourceId))
					.jwtAuthenticationConverter(jwtAuthenticationConverter());
		}
	}

	private NimbusJwtDecoder jwtDecoder(String oauth2JwksUri, String resourceId) {
		String usernameClaim = environment.getProperty(PROP_OAUTH2_USERNAME_ATTRIBUTE, "sub");
		OAuth2TokenValidator<Jwt> audienceValidator = token -> {
			if (token.getAudience().contains(resourceId)) {
				return OAuth2TokenValidatorResult.success();
			} else {
				return OAuth2TokenValidatorResult.failure(new OAuth2Error("custom_code", "Invalid audience", null));
			}
		};

		OAuth2TokenValidator<Jwt> usernameValidator = token -> {
			if (token.hasClaim(usernameClaim)) {
				return OAuth2TokenValidatorResult.success();
			} else {
				return OAuth2TokenValidatorResult.failure(new OAuth2Error("custom_code", "Username claim missing", null));
			}
		};

		DelegatingOAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(Arrays.asList(new JwtTimestampValidator(), audienceValidator, usernameValidator));

		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(oauth2JwksUri).build();
		decoder.setJwtValidator(validators);

		return decoder;
	}

	private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
		String rolesClaim = environment.getProperty(PROP_OAUTH2_ROLES_CLAIM);
		String usernameClaim = environment.getProperty(PROP_OAUTH2_USERNAME_ATTRIBUTE, "sub");
		return source -> {
			Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
			if (rolesClaim != null) {
				Object claimValue = source.getClaim(rolesClaim);
				for (String role : OpenIDAuthenticationBackend.parseRolesClaim(logger,  rolesClaim, claimValue)) {
					String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
					mappedAuthorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
				}
			}

			String principalClaimValue = source.getClaimAsString(usernameClaim);
			if (principalClaimValue == null) {
				throw new IllegalArgumentException(String.format("Cannot extract username from OAuth token, no claim %s found", usernameClaim));
			}
			return new JwtAuthenticationToken(source, mappedAuthorities, principalClaimValue);
		};
	}

	@Bean(name="authenticationManager")
	@ConditionalOnExpression("'${proxy.authentication}' == 'keycloak'")
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}

	@Bean
	public SavedRequestAwareAuthenticationSuccessHandler SavedRequestAwareAuthenticationSuccessHandler() {
		SavedRequestAwareAuthenticationSuccessHandler savedRequestAwareAuthenticationSuccessHandler = new SavedRequestAwareAuthenticationSuccessHandler();
		savedRequestAwareAuthenticationSuccessHandler.setDefaultTargetUrl(AUTH_SUCCESS_URL);
		return savedRequestAwareAuthenticationSuccessHandler;
	}

	private List<Header> getCustomHeaders() {
		List<Header> headers = new ArrayList<>();

		int i = 0;
		String headerName = environment.getProperty(String.format(PROP_CUSTOM_HEADERS + "[%d].name", i));
		while (headerName != null) {
			String headerValue = environment.getProperty(String.format(PROP_CUSTOM_HEADERS + "[%d].value", i));
			if (headerValue == null) {
				logger.warn("Missing header value for header {}", headerName);
				i++;
				continue;
			}
			headers.add(new Header(headerName, headerValue));
			i++;
			headerName = environment.getProperty(String.format(PROP_CUSTOM_HEADERS + "[%d].name", i));
		}

		return headers;
	}

}
