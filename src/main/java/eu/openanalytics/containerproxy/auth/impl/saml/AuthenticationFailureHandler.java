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

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.saml.SAMLStatusException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler  {

    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {


        if (exception.getCause() instanceof SAMLStatusException) {
            SAMLStatusException samlException = (SAMLStatusException) exception.getCause();

            if (samlException.getStatusCode().equals("urn:oasis:names:tc:SAML:2.0:status:RequestDenied")) {
                response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/app-access-denied").build().toUriString());
                return;
            }
        }

        super.onAuthenticationFailure(request, response, exception);
    }

}
