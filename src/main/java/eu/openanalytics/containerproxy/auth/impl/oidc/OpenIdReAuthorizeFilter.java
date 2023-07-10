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
package eu.openanalytics.containerproxy.auth.impl.oidc;

import eu.openanalytics.containerproxy.util.ImmediateJsonResponse;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import static eu.openanalytics.containerproxy.auth.impl.oidc.OpenIDConfiguration.REG_ID;

/**
 * Ensures that the access token of the user is refreshed when needed.
 * If the refresh token is invalid (e.g. because the session in the IdP expired), we first invalidate the session and then throw a
 * {@link ClientAuthorizationRequiredException} exception such that the user can re-login.
 * This filter only applies to a limited set of requests and not to requests that are proxied to apps.
 * Otherwise, this filter would be called too much and cause too much overhead. In addition, this filter should
 * only be used on non-ajax requests. This is required for the redirect to the IDP to properly work.
 *
 * A special case is the /refresh-openid endpoint, which does not throw the exception (i.e. it does not cause a redirect to the IDP)
 * but only invalidates the session. This endpoint is frequently called (at least every <40 seconds) when the user is using an app.
 * It refreshes the OIDC session as long as possible and when it fails (e.g. because the session was revoked or reached it max life),
 * the session is invalidated. The app page detects this and shows a message that the user was logged out.
 *
 * See #30569, #28976
 */
public class OpenIdReAuthorizeFilter extends OncePerRequestFilter {

    private static final RequestMatcher REFRESH_OPENID_MATCHER = new AntPathRequestMatcher("/refresh-openid");

    private static final RequestMatcher REQUEST_MATCHER = new OrRequestMatcher(
            new AntPathRequestMatcher("/app/**"),
            new AntPathRequestMatcher("/app_i/**"),
            new AntPathRequestMatcher("/"),
            REFRESH_OPENID_MATCHER);

    @Inject
    private OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    @Inject
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Inject
    private Environment environment;

    private final Clock clock = Clock.systemUTC();

    // use clock skew of 40 seconds instead of 60 seconds. Otherwise, if the access token is valid for 1 minute, it would get refreshed at each request.
    private final Duration clockSkew = Duration.ofSeconds(40);

    private boolean ignoreLogout;

    @PostConstruct
    public void init() {
        ignoreLogout = environment.getProperty("proxy.openid.ignore-session-expire", Boolean.class, false);
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain chain) throws ServletException, IOException {
        if (REQUEST_MATCHER.matches(request)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth instanceof OAuth2AuthenticationToken) {
                OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(REG_ID, auth.getName());

                if (authorizedClient == null) {
                    if (!ignoreLogout) {
                        invalidateSession(request, response, auth);
                        return;
                    }
                } else {
                    if (accessTokenExpired(authorizedClient)) {
                        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                                .withAuthorizedClient(authorizedClient)
                                .principal(auth)
                                .build();

                        try {
                            oAuth2AuthorizedClientManager.authorize(authorizeRequest);
                            logger.debug(String.format("OpenID access token refreshed [user: %s]", auth.getName()));
                        } catch (ClientAuthorizationException ex) {
                            if (!ignoreLogout) {
                                invalidateSession(request, response, auth);
                                return;
                            } else {
                                logger.debug(String.format("OpenID access token expired, internal session stays active [user: %s]", auth.getName()));
                            }
                        }
                    }
                }
            }
        }
        if (REFRESH_OPENID_MATCHER.matches(request)) {
            ImmediateJsonResponse.write(response, 200, "{\"status\":\"success\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * See {@link RefreshTokenOAuth2AuthorizedClientProvider}
     */
    private boolean accessTokenExpired(OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient == null || authorizedClient.getAccessToken() == null || authorizedClient.getAccessToken().getExpiresAt() == null) {
            return true;
        }
        return clock.instant().isAfter(authorizedClient.getAccessToken().getExpiresAt().minus(this.clockSkew));
    }

    private void invalidateSession(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, Authentication auth) throws IOException {
        logger.debug(String.format("OpenID access token expired, invalidating internal session [user: %s]", auth.getName()));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        if (REFRESH_OPENID_MATCHER.matches(request)) {
            ImmediateJsonResponse.write(response, 200, "{\"status\":\"success\"}");
        } else {
            throw new ClientAuthorizationRequiredException(REG_ID);
        }
    }

}
