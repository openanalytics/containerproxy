/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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

import eu.openanalytics.containerproxy.auth.impl.SAMLAuthenticationBackend;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.saml2.provider.service.web.authentication.logout.Saml2LogoutRequestFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

/**
 * Filter that disables the {@link Saml2LogoutRequestFilter} filter, except for the SAML single logout endpoint.
 * The SAML filter calls getParameter and thefore consumes the POST  body.
 * The name of {@link Saml2LogoutRequestFilter must be fixed in order for this to work (see {@link SAMLAuthenticationBackend}
 * See #33066.
 */
public class DisableSaml2LogoutRequestFilterFilter extends GenericFilterBean {

    private static final RequestMatcher REQUEST_MATCHER = new OrRequestMatcher(
        new AntPathRequestMatcher("/logout/saml2/slo"),
        new AntPathRequestMatcher("/logout/saml2/slo/*")
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!REQUEST_MATCHER.matches((HttpServletRequest) request)) {
            // set the filtered as already being executed, this is the only way to disable the filter
            request.setAttribute("Saml2LogoutRequestFilter.FILTERED", true);
        }
        chain.doFilter(request, response);
    }

}
