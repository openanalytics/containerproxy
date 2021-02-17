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
package eu.openanalytics.containerproxy.auth.impl.saml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensaml.common.SAMLException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml.SAMLStatusException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;

import static eu.openanalytics.containerproxy.auth.impl.saml.AlreadyLoggedInFilter.REQ_PROP_AUTH_BEFORE_SSO;

public class AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final Logger logger = LogManager.getLogger(getClass());

    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {

        if (exception.getCause() instanceof SAMLStatusException) {
            SAMLStatusException samlException = (SAMLStatusException) exception.getCause();

            if (samlException.getStatusCode().equals("urn:oasis:names:tc:SAML:2.0:status:RequestDenied")) {
                response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/app-access-denied").build().toUriString());
                return;
            }

        } else if (exception.getCause() instanceof SAMLException) {
            SAMLException samlException = (SAMLException) exception.getCause();

            if (isOrWasAuthenticated(request) && (
                       samlException.getMessage().startsWith("Response issue time is either too old or with date in the future")
                    || samlException.getMessage().startsWith("InResponseToField of the Response doesn't correspond to sent message"))
                    || samlException.getMessage().equals("Unsupported request")) {
                response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/").build().toUriString());
                return;
            } else if (samlException.getCause() instanceof CredentialsExpiredException) {
                logger.warn("The credentials of the user has expired, this typically indicates a misconfiguration, see https://shinyproxy.io/faq/#the-credentials-of-the-user-expire-when-using-saml for more information!");
                response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/auth-error").build().toUriString());
                return;
            }
        }

        super.onAuthenticationFailure(request, response, exception);
    }

    private boolean isOrWasAuthenticated(HttpServletRequest request) {
        if (request.getAttribute(REQ_PROP_AUTH_BEFORE_SSO).equals("true")) {
            // Before doing a SSO request we check whether the user is authenticated, if so we set the SP_REQ_PROP_AUTH_BEFORE_SSO
            // property. If the auth failed, the Spring SecurityContext is cleared and thus we cannot use that to
            // check whether the user is authenticated.
            return true;
        }

        HttpSession session = request.getSession();
        Object obj = session.getAttribute("SPRING_SECURITY_CONTEXT");
        if (obj instanceof SecurityContext) {
            SecurityContext ctx = (SecurityContext) obj;
            Authentication auth = ctx.getAuthentication();
            // in some cases the session may still contain the security context so we fallback to that
            return auth != null && auth.getPrincipal() != null;
        }

        return false;
    }

}
