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

import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

public class BaseController {

    @Inject
    private Environment environment;

    @Inject
    private IdentifierService identifierService;

    @Inject
    protected SpecExpressionResolver expressionResolver;

    @Inject
    private UserService userService;

    private String title;
    private boolean titleContainsExpression;

    @PostConstruct
    public void baseInit() {
        title = environment.getProperty("proxy.title", "ShinyProxy");
        titleContainsExpression = title.contains("#{");
    }

    protected void prepareMap(ModelMap map) {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = servletRequestAttributes.getRequest();
        HttpServletResponse httpServletResponse = servletRequestAttributes.getResponse();
        map.put("title", getTitle(userService.getCurrentAuth(), httpServletRequest.getServerName()));
        // no versioning (using instanceId) needed since paths already contain a version
        map.put("bootstrapCss", "/css/bootstrap.css");
        map.put("bootstrapJs", "/js/bootstrap.js");
        map.put("jqueryJs", "/webjars/jquery/3.7.1/jquery.min.js");
        map.put("fontAwesomeCss", "/webjars/fontawesome/4.7.0/css/font-awesome.min.css");
        map.put("resourcePrefix", "/" + identifierService.instanceId);

        map.put("request", httpServletRequest);
        map.put("response", httpServletResponse);
    }

    private String getTitle(Authentication user, String serverName) {
        if (!titleContainsExpression) {
            return title;
        }
        SpecExpressionContext context = SpecExpressionContext.create(
                user,
                user.getPrincipal(),
                user.getCredentials()
            )
            .serverName(serverName)
            .build();
        return expressionResolver.evaluateToString(title, context);
    }

}
