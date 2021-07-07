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
import org.apache.commons.io.IOUtils;
import org.springframework.core.env.Environment;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * While the recovery is happening, the application may not be used.
 * Therefore this filter returns a 503 as long as the app recovery is in progress.
 */
@Component
public class AppRecoveryFilter extends GenericFilterBean {

    @Inject
    public AppRecoveryService appRecoveryService;

    @Inject
    public Environment environment;

    private String renderedTemplate;

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
            // ... but it is a request to actuator -> let it pass to make the probes work properly
            chain.doFilter(request, response);
            return;
        }

        // ... generate a 503
        renderTemplate((HttpServletResponse) response);
    }

    private void renderTemplate(HttpServletResponse httpResponse ) throws IOException {
        if (renderedTemplate == null) {
            InputStream template = this.getClass().getResourceAsStream("/templates/startup.html");
            if (template == null) {
                throw new IllegalStateException("Startup template should be available");
            }

            String applicationName = environment.getProperty("spring.application.name");
            if (applicationName == null) {
                throw new IllegalStateException("Application name should be available"); // we provide a default so this should not happen
            }

            renderedTemplate = IOUtils.toString(template, StandardCharsets.UTF_8.name());
            renderedTemplate = renderedTemplate.replace("${application_name}", applicationName);
        }

        httpResponse.setStatus(503);
        httpResponse.setContentType("text/html");

        IOUtils.write(renderedTemplate, httpResponse.getOutputStream(), StandardCharsets.UTF_8.name());
    }

}

