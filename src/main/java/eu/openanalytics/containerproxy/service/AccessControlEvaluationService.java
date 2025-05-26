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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.model.spec.AccessControl;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AccessControlEvaluationService {

    public static final String PROP_USERNAME_CASE_SENSITIVE = "proxy.username-case-sensitive";

    private final IAuthenticationBackend authBackend;
    private final UserService userService;

    private final SpecExpressionResolver specExpressionResolver;
    private final Boolean usernameCaseSensitive;

    public AccessControlEvaluationService(@Lazy IAuthenticationBackend authBackend, UserService userService, SpecExpressionResolver specExpressionResolver, Environment environment) {
        this.authBackend = authBackend;
        this.userService = userService;
        this.specExpressionResolver = specExpressionResolver;
        usernameCaseSensitive = environment.getProperty(PROP_USERNAME_CASE_SENSITIVE, Boolean.class, true);
    }

    public boolean checkAccess(Authentication auth, ProxySpec spec, AccessControl accessControl, Object... objects) {
        if (auth instanceof AnonymousAuthenticationToken) {
            // if anonymous -> only allow access if the backend has no authorization enabled
            if (authBackend.hasAuthorization()) {
                return false;
            }
        }

        if (hasNoAccessControl(accessControl)) {
            return true;
        }

        if (allowedByGroups(auth, accessControl)) {
            return true;
        }

        if (allowedByUsers(auth, accessControl)) {
            return true;
        }

        return allowedByExpression(auth, spec, accessControl, objects);
    }

    public boolean hasNoAccessControl(AccessControl accessControl) {
        if (accessControl == null) {
            return true;
        }

        return !accessControl.hasGroupAccess()
            && !accessControl.hasUserAccess()
            && !accessControl.hasExpressionAccess();
    }

    public boolean allowedByGroups(Authentication auth, AccessControl accessControl) {
        if (!accessControl.hasGroupAccess()) {
            // no groups defined -> this user has no access based on the groups
            return false;
        }
        for (String group : accessControl.getGroups()) {
            if (userService.isMember(auth, group)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByUsers(Authentication auth, AccessControl accessControl) {
        if (!accessControl.hasUserAccess()) {
            // no users defined -> this user has no access based on the users
            return false;
        }
        for (String user : accessControl.getUsers()) {
            if (usernameEquals(auth.getName(), user)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByExpression(Authentication auth, ProxySpec spec, AccessControl accessControl, Object... objects) {
        if (!accessControl.hasExpressionAccess()) {
            // no expression defined -> this user has no access based on the expression
            return false;
        }
        SpecExpressionContext.SpecExpressionContextBuilder contextBuilder = SpecExpressionContext
            .create(objects)
            .addServerName()
            .extend(spec);

        if (auth != null) {
            contextBuilder.extend(auth, auth.getPrincipal(), auth.getCredentials());
        }
        return specExpressionResolver.evaluateToBoolean(accessControl.getExpression(), contextBuilder.build());
    }

    public boolean usernameEquals(String authenticatedUser, String other) {
        if (usernameCaseSensitive) {
            return authenticatedUser.equals(other);
        }
        return authenticatedUser.equalsIgnoreCase(other);
    }

}
