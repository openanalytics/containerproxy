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
package eu.openanalytics.containerproxy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.savedrequest.RequestCacheAwareFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;

/**
 * Prevents the {@link RequestCacheAwareFilter} from filtering requests that get proxied.
 * The filter calls getParameter() which consumes the request body and therefore, when the request is proxied,
 * this causes a {@link io.undertow.server.TruncatedResponseException}.
 * See #31735
 */
public class FixedRequestCacheAwareFilter extends RequestCacheAwareFilter {

    private static final RequestMatcher REQUEST_MATCHER = new OrRequestMatcher(
        new AntPathRequestMatcher("/app_proxy/**"),
        new AntPathRequestMatcher("/app_direct/**"),
        new AntPathRequestMatcher("/api/route/**")
    );
    private final RequestCacheAwareFilter delegate;

    public FixedRequestCacheAwareFilter(RequestCacheAwareFilter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        if (!REQUEST_MATCHER.matches((HttpServletRequest) servletRequest)) {
            delegate.doFilter(servletRequest, servletResponse, chain);
            return;
        }
        chain.doFilter(servletRequest, servletResponse);
    }
}
