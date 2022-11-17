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
package eu.openanalytics.containerproxy.auth.impl.oidc;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static eu.openanalytics.containerproxy.auth.impl.oidc.OpenIDConfiguration.REG_ID;

/**
 * Ensures that the access token of the user is refreshed when needed.
 * If the refresh token is invalid (e.g. because the session in the IdP expired), we throw a
 * {@link ClientAuthorizationRequiredException} exception such that the user is can re-login.
 *
 * This filter only applies to a limited set of requests and not to requests that are proxied to apps.
 * Otherwise, this filter would be called too much and cause too much overhead.
 */
public class OpenIdReAuthorizeFilter extends GenericFilterBean {

    private static final RequestMatcher REQUEST_MATCHER = new OrRequestMatcher(
            new AntPathRequestMatcher("/app/**"),
            new AntPathRequestMatcher("/app_i/**"),
            new AntPathRequestMatcher("/"),
            new AntPathRequestMatcher("/heartbeat"));

    @Inject
    private OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (REQUEST_MATCHER.matches((HttpServletRequest) request)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof OAuth2AuthenticationToken) {
                OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                        .withClientRegistrationId(REG_ID)
                        .principal(auth)
                        .attribute(HttpServletRequest.class.getName(), request)
                        .attribute(HttpServletResponse.class.getName(), response)
                        .build();

                // re-authorize
                try {
                    oAuth2AuthorizedClientManager.authorize(authorizeRequest);
                } catch (ClientAuthorizationException ex) {
                    if (ex.getError().getErrorCode().equals(OAuth2ErrorCodes.INVALID_GRANT)) {
                        // if refresh token has expired or is invalid -> re-start authorization process
                        throw new ClientAuthorizationRequiredException(ex.getClientRegistrationId());
                    }
                    throw ex;
                }
            }
        }
        chain.doFilter(request, response);
    }

}
