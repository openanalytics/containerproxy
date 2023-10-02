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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.util.ImmediateJsonResponse;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import eu.openanalytics.containerproxy.util.ContextPathHelper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class ProxyRouteController extends BaseController {

	@Inject
	private UserService userService;
	
	@Inject
	private ProxyService proxyService;
	
	@Inject
	private ProxyMappingManager mappingManager;
	
	@RequestMapping(value="/api/route/**")
	public void route(HttpServletRequest request, HttpServletResponse response) {
		try {
			// Ensure that the caller is the owner of the target proxy.
			boolean hasAccess = false;
			String baseURL = ContextPathHelper.withEndingSlash() + "api/route/";
			String mapping = request.getRequestURI().substring(baseURL.length());
			String proxyId = mappingManager.getProxyId(mapping);
			if (proxyId != null) {
				Proxy proxy = proxyService.findProxy(p -> proxyId.equals(p.getId()), false);
				hasAccess = userService.isOwner(proxy);
			}
			
			if (hasAccess) {
				mappingManager.dispatchAsync(proxyId, mapping, request, response);
			} else {
				response.setStatus(403);
				response.getWriter().write("Not authorized to access this proxy");
			}
		} catch (Exception e) {
			throw new RuntimeException("Error routing proxy request", e);
		}
	}
}
