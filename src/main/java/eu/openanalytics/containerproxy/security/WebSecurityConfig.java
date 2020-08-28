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
package eu.openanalytics.containerproxy.security;

import java.util.List;

import javax.inject.Inject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.UserLogoutHandler;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Inject
	private UserLogoutHandler logoutHandler;

	@Inject
	private IAuthenticationBackend auth;
	
	@Inject
	private AuthenticationEventPublisher eventPublisher;
	
	@Inject
	private Environment environment;
	
	@Autowired(required=false)
	private List<ICustomSecurityConfig> customConfigs;
	
	@Override
	public void configure(WebSecurity web) throws Exception {
//		web
//			.ignoring().antMatchers("/css/**").and()
//			.ignoring().antMatchers("/img/**").and()
//			.ignoring().antMatchers("/js/**").and()
//			.ignoring().antMatchers("/assets/**").and()
//			.ignoring().antMatchers("/webjars/**").and();
//		
		if (customConfigs != null) {
			for (ICustomSecurityConfig cfg: customConfigs) cfg.apply(web);
		}
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		// Perform CSRF check on the login form
		http.csrf().requireCsrfProtectionMatcher(new AntPathRequestMatcher("/login", "POST"));
		
		// Always set header: X-Content-Type-Options=nosniff
		http.headers().contentTypeOptions();

		String frameOptions = environment.getProperty("server.frameOptions", "disable");
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
		
		// Note: call early, before http.authorizeRequests().anyRequest().fullyAuthenticated();
		if (customConfigs != null) {
			for (ICustomSecurityConfig cfg: customConfigs) cfg.apply(http);
		}

		if (auth.hasAuthorization()) {
			http.authorizeRequests().antMatchers(
						"/login", "/signin/**",
						"/favicon.ico", "/css/**", "/img/**", "/js/**", "/assets/**", "/webjars/**").permitAll();
			http.authorizeRequests().anyRequest().fullyAuthenticated();

			http
				.formLogin()
					.loginPage("/login")
					.and()
				.logout()
					.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
					.addLogoutHandler(logoutHandler)
					.logoutSuccessUrl(auth.getLogoutSuccessURL());
			
			// Enable basic auth for RESTful calls when APISecurityConfig is not enabled.
			http.addFilter(new BasicAuthenticationFilter(authenticationManagerBean()));
		}
		
		auth.configureHttpSecurity(http);
	}

	@Bean
	public GlobalAuthenticationConfigurerAdapter authenticationConfiguration() {
		return new GlobalAuthenticationConfigurerAdapter() {
			@Override
			public void init(AuthenticationManagerBuilder amb) throws Exception {
				amb.authenticationEventPublisher(eventPublisher);
				auth.configureAuthenticationManagerBuilder(amb);
			}
		};
	}
	
	@Bean(name="authenticationManager")
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}
}