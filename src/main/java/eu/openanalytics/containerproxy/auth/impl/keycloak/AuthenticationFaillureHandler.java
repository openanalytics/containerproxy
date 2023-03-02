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
package eu.openanalytics.containerproxy.auth.impl.keycloak;

import org.keycloak.adapters.OIDCAuthenticationError;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationFailureHandler;
import org.springframework.security.core.AuthenticationException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthenticationFaillureHandler extends KeycloakAuthenticationFailureHandler {

    final public static String SP_KEYCLOAK_ERROR_REASON = "SP_KEYCLOAK_ERROR_REASON";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        // Note: Keycloak calls sendError before this method gets called, therefore we cannot do much with reuqest.
        // We now set a flag in the session indicating the reason of the Keycloak error.
        // The error page can then properly handle this.

        Object obj = request.getAttribute("org.keycloak.adapters.spi.AuthenticationError");
        if (obj instanceof org.keycloak.adapters.OIDCAuthenticationError) {
            OIDCAuthenticationError authError = (OIDCAuthenticationError)  obj;
            request.getSession().setAttribute(SP_KEYCLOAK_ERROR_REASON, authError.getReason());
        }
    }
}
