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
package eu.openanalytics.containerproxy.api;

import eu.openanalytics.containerproxy.backend.strategy.impl.DefaultTargetMappingStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.util.ContextPathHelper;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.inject.Inject;

@Controller
public class ProxyRouteController extends BaseController {

    @Inject
    private ContextPathHelper contextPathHelper;

    @Inject
    private ProxyService proxyService;

    @Inject
    private ProxyMappingManager mappingManager;

    @RequestMapping(value = "/api/route/{targetId}/**")
    public void route(@PathVariable String targetId, HttpServletRequest request, HttpServletResponse response) {
        try {
            String baseURL = contextPathHelper.withEndingSlash() + "api/route/";
            String mapping = request.getRequestURI().substring(baseURL.length() + DefaultTargetMappingStrategy.TARGET_ID_LENGTH);
            Proxy proxy = proxyService.getUserProxyByTargetId(targetId);

            if (proxy != null) {
                mappingManager.dispatchAsync(proxy.getId(), mapping, request, response);
            } else {
                response.setStatus(403);
                response.getWriter().write("Not authorized to access this proxy");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error routing proxy request", e);
        }
    }
}
