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


import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.*;
import java.io.IOException;

/**
 * A filter that sets a request attribute when the user is already logged in.
 * This is used to know whether a user was already logged in when performing a SAML SSO request.
 * If so, the user probably has clicked the back button and should therefore be redirected to the main page.
 *
 * Note: we don't redirect to the main page here, because that seems to be to intrusive. There may be good reasons
 * to land on the SSO page again.
 */
public class AlreadyLoggedInFilter extends GenericFilterBean {

    public static final String REQ_PROP_AUTH_BEFORE_SSO = "SP_REQ_PROP_AUTH_BEFORE_SSO";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !(auth instanceof AnonymousAuthenticationToken)) {
            request.setAttribute(REQ_PROP_AUTH_BEFORE_SSO, "true");
        }
        chain.doFilter(request, response);
    }

}

