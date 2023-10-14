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

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.customHeader.CustomHeaderAuthenticationFilter;
import eu.openanalytics.containerproxy.auth.impl.customHeader.CustomHeaderAuthenticationToken;

@Component
public class CustomHeaderAuthenticationBackend implements IAuthenticationBackend{

    public final static String NAME = "customHeader";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasAuthorization() {
        return false;
    }

    @Override
    public void configureHttpSecurity(HttpSecurity http, AuthorizedUrl anyRequestConfigurer) throws Exception {
        http.formLogin().disable();
		
		http.addFilterBefore(new CustomHeaderAuthenticationFilter(), BasicAuthenticationFilter.class);
    }

    @Override
    public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
        // Configure a custom Authentication Provider
        CustomHeaderAuthenticationProvider authenticationProvider = new CustomHeaderAuthenticationProvider();
        
		auth.authenticationProvider(authenticationProvider);  
    }

 
    public class CustomHeaderAuthenticationProvider implements 
        org.springframework.security.authentication.AuthenticationProvider, 
        org.springframework.beans.factory.InitializingBean {

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            CustomHeaderAuthenticationToken token = (CustomHeaderAuthenticationToken) authentication;
            if (token.isValid()) 
                return new CustomHeaderAuthenticationToken(token.getPrincipal().toString());
            
            throw new BadCredentialsException("Invalid username");
            
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return false;
        }

        @Override
        public void afterPropertiesSet() throws Exception {

        }
    }
    
}
