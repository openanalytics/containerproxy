/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2019 Open Analytics
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
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
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
	
	@Autowired(required=false)
	private List<ICustomSecurityConfig> customConfigs;
	
	@Override
	public void configure(WebSecurity web) throws Exception {
		web
			.ignoring().antMatchers("/css/**").and()
			.ignoring().antMatchers("/img/**").and()
			.ignoring().antMatchers("/js/**").and()
			.ignoring().antMatchers("/assets/**").and()
			.ignoring().antMatchers("/webjars/**").and();
		
		if (customConfigs != null) {
			for (ICustomSecurityConfig cfg: customConfigs) cfg.apply(web);
		}
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
			// must disable or handle in proxy
			.csrf().disable()
			// disable X-Frame-Options
			.headers().frameOptions().disable();

		// Note: call early, before http.authorizeRequests().anyRequest().fullyAuthenticated();
		if (customConfigs != null) {
			for (ICustomSecurityConfig cfg: customConfigs) cfg.apply(http);
		}

		if (auth.hasAuthorization()) {
			http.authorizeRequests().antMatchers("/login", "/signin/**").permitAll();
			http.authorizeRequests().anyRequest().fullyAuthenticated();

			http
				.formLogin()
					.loginPage("/login")
					.and()
				.logout()
					.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
					.addLogoutHandler(logoutHandler)
					.logoutSuccessUrl(auth.getLogoutSuccessURL());
			
			// Enable basic auth for RESTful calls
			//TODO Restful calls with basic auth will generate a new "login" every time
//			http.addFilter(new BasicAuthenticationFilter(authenticationManagerBean()));
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