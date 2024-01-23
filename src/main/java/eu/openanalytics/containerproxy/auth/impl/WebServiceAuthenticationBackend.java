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
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import javax.inject.Inject;

/**
 * Web service authentication method where user/password combinations are
 * checked by a HTTP call to a remote web service.
 */
public class WebServiceAuthenticationBackend implements IAuthenticationBackend {

    public static final String NAME = "webservice";

    private static final String PROPERTY_PREFIX = "proxy.webservice.";

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
    public void configureHttpSecurity(HttpSecurity http) {
        // Nothing to do.
    }

    @Override
    public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) {
        // TODO re-implement #31084
//        RemoteAuthenticationProvider authenticationProvider = new RemoteAuthenticationProvider();
//        authenticationProvider.setRemoteAuthenticationManager((username, password) -> {
//            RestTemplate restTemplate = new RestTemplate();
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
//            headers.setContentType(MediaType.APPLICATION_JSON);
//
//            try {
//                String body = String.format(environment.getProperty(PROPERTY_PREFIX + "authentication-request-body", ""), username, password);
//                String loginUrl = environment.getProperty(PROPERTY_PREFIX + "authentication-url");
//                ResponseEntity<String> result = restTemplate.exchange(loginUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
//                if (result.getStatusCode() == HttpStatus.OK) {
//                    return Lists.newArrayList();
//                }
//                throw new AuthenticationServiceException("Unknown response received " + result);
//            } catch (HttpClientErrorException e) {
//                throw new BadCredentialsException("Invalid username or password");
//            } catch (RestClientException e) {
//                throw new AuthenticationServiceException("Internal error " + e.getMessage());
//            }
//
//        });
//        auth.authenticationProvider(authenticationProvider);
    }

}
