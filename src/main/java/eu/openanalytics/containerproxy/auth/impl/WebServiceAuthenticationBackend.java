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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Web service authentication method where user/password combinations are
 * checked by a HTTP call to a remote web service.
 */
public class WebServiceAuthenticationBackend implements IAuthenticationBackend {

    public static final String NAME = "webservice";

    private static final String PROP_PREFIX = "proxy.webservice.";
    private static final String PROP_AUTHENTICATION_REQUEST_BODY = PROP_PREFIX + "authentication-request-body";
    private static final String PROP_AUTHENTICATION_URL = PROP_PREFIX + "authentication-url";
    private static final String PROP_GROUPS_EXPRESSION = PROP_PREFIX + "groups-expression";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String requestBodyTemplate;
    private final String authenticationUrl;
    private final String groupsExpression;

    @Inject
    private SpecExpressionResolver specExpressionResolver;

    public WebServiceAuthenticationBackend(Environment environment) {
        requestBodyTemplate = environment.getProperty(PROP_AUTHENTICATION_REQUEST_BODY);
        if (requestBodyTemplate == null) {
            throw new IllegalStateException("Webservice authentication enabled, but no '" + PROP_AUTHENTICATION_REQUEST_BODY + "' defined!");
        }
        authenticationUrl = environment.getProperty(PROP_AUTHENTICATION_URL);
        if (authenticationUrl == null) {
            throw new IllegalStateException("Webservice authentication enabled, but no '" + PROP_AUTHENTICATION_URL + "' defined!");
        }
        groupsExpression = environment.getProperty(PROP_GROUPS_EXPRESSION);
    }

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
        auth.authenticationProvider(new WebServiceAuthenticationProvider());
    }

    public class WebServiceAuthenticationProvider implements AuthenticationProvider {

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            String username = authentication.getName();
            String password = authentication.getCredentials().toString();

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);

            try {
                String body = String.format(requestBodyTemplate, username, password);
                ResponseEntity<String> result = restTemplate.exchange(authenticationUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
                if (result.getStatusCode() == HttpStatus.OK) {
                    User user = createUser(username, result.getBody());
                    return new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities());
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
            // Return true if this AuthenticationProvider supports the provided authentication class
            return authentication.equals(UsernamePasswordAuthenticationToken.class);
        }

        private User createUser(String username, String body) {
            if (body == null) {
                return new WebServiceUser(username, null, null, List.of());
            }
            JsonNode jsonResponse = null;
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            try {
                jsonResponse = objectMapper.readTree(body);
                if (groupsExpression != null) {
                    SpecExpressionContext context = SpecExpressionContext.create(jsonResponse);
                    List<String> groups = specExpressionResolver.evaluateToList(List.of(groupsExpression), context);
                    for (String role: groups) {
                        String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
                        authorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
                    }
                }
            } catch (JsonProcessingException e) {
                logger.warn("Invalid json response returned by web service, response is: " + body, e);
            }
            return new WebServiceUser(username, body, jsonResponse, authorities);
        }
    }

    public static class WebServiceUser extends User {

        private final String response;
        private final JsonNode jsonResponse;

        public WebServiceUser(String username, String response, JsonNode jsonResponse, Collection<? extends GrantedAuthority> authorities) {
            super(username, "", authorities);
            this.response = response;
            this.jsonResponse = jsonResponse;
        }

        public String getResponse() {
            return response;
        }

        public JsonNode getJsonResponse() {
            return jsonResponse;
        }
    }

}
