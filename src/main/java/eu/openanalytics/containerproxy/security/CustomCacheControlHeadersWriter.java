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
package eu.openanalytics.containerproxy.security;

import eu.openanalytics.containerproxy.service.ProxyCacheHeadersService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;

/**
 * Custom implementation of {@link CacheControlHeadersWriter} that works with proxied requests.
 *
 * By default Spring Security adds "no-cache" headers to any response that does not yet have any cache headers defined.
 * The reason is that in most cases nothing should be cached when a user is authenticated,
 * because the cache may be persisted when the user is logged out.
 * However, this does not work for proxied requests. The {@link CacheControlHeadersWriter#writeHeaders(HttpServletRequest, HttpServletResponse)}
 * method checks whether any cache headers are included in the response. But due to the async behaviour of the undertow proxy,
 * the real response headers are not yet added to the response, and thus the headers are always added.
 * Therefore, this custom class does not add the headers to any proxied request.
 * The {@link ProxyCacheHeadersService} adds cache headers to proxied requests.
 */
public class CustomCacheControlHeadersWriter implements HeaderWriter {

    private final HeaderWriter delegate;

    public CustomCacheControlHeadersWriter() {
        this.delegate = new CacheControlHeadersWriter();
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (request.getServletPath().startsWith("/proxy_endpoint/")) {
            return;
        }
        this.delegate.writeHeaders(request, response);
    }

}
