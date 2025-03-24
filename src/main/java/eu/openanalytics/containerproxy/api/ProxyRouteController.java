/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
import eu.openanalytics.containerproxy.service.UserAndTargetIdProxyIndex;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.util.ContextPathHelper;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ProxyRouteController extends BaseController {

    private final ProxyMappingManager mappingManager;

    private final UserAndTargetIdProxyIndex userAndTargetIdProxyIndex;

    private final UserService userService;

    private final int baseUrlLength;

    public ProxyRouteController(ContextPathHelper contextPathHelper, ProxyMappingManager mappingManager, UserAndTargetIdProxyIndex userAndTargetIdProxyIndex, UserService userService) {
        this.mappingManager = mappingManager;
        this.userAndTargetIdProxyIndex = userAndTargetIdProxyIndex;
        this.userService = userService;
        String baseURL = contextPathHelper.withEndingSlash() + "api/route/";
        baseUrlLength = baseURL.length() + DefaultTargetMappingStrategy.TARGET_ID_LENGTH + 1;
    }

    @RequestMapping(value = "/api/route/{targetId}/**")
    public void route(@PathVariable String targetId, HttpServletRequest request, HttpServletResponse response) {
        try {
            if (request.getRequestURI().length() < baseUrlLength) {
                response.setStatus(403);
                response.getWriter().write("Not authorized to access this proxy");
                return;
            }

            String mapping = request.getRequestURI().substring(baseUrlLength);
            Proxy proxy = userAndTargetIdProxyIndex.getProxy(userService.getCurrentUserId(), targetId);

            if (proxy != null) {
                mappingManager.dispatchAsync(proxy, mapping, request, response);
            } else {
                response.setStatus(403);
                response.getWriter().write("Not authorized to access this proxy");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error routing proxy request", e);
        }
    }

}
