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
package eu.openanalytics.containerproxy.util;

import eu.openanalytics.containerproxy.service.AppRecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * While the recovery is happening, the application may not be used.
 * Therefore this filter returns a 503 as long as the app recovery is in progress.
 */
@Component
public class AppRecoveryFilter extends GenericFilterBean {

    @Inject
    public AppRecoveryService appRecoveryService;

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        if (appRecoveryService.isReady()) {
            // App Recovery is ready -> continue the application
            chain.doFilter(request, response);
            return;
        }

        // App Recovery is not yet ready ...

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getServletPath();
        if (path != null && path.startsWith("/actuator")) {
            // ... but it is a request to actuator -> let it pass to make the probes work properyl
            chain.doFilter(request, response);
            return;
        }

        // ... generate a 503
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(503);
        httpResponse.setContentType("text/html");
        httpResponse.getWriter().write("<h1>ShinyProxy is starting up, check back in a few seconds.</h1>");
    }

}

