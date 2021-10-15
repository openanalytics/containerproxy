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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.model.spec.ProxyAccessControl;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {

    private final IAuthenticationBackend authBackend;
    private final UserService userService;
    private final IProxySpecProvider specProvider;

    public AccessControlService(@Lazy IAuthenticationBackend authBackend, UserService userService, IProxySpecProvider specProvider) {
        this.authBackend = authBackend;
        this.userService = userService;
        this.specProvider = specProvider;
    }

    public boolean canAccess(Authentication auth, String specId) {
        return canAccess(auth, specProvider.getSpec(specId));
    }

    public boolean canAccess(Authentication auth, ProxySpec spec) {
        Optional<String> sessionId = getSessionId();
        if (!sessionId.isPresent()) {
            return checkAccess(auth, spec);
        }
        // we got a sessionId -> use the cache
        return authorizationCache.computeIfAbsent(
                Pair.of(sessionId.get(), spec.getId()),
                (k) -> checkAccess(auth, spec));
    }

    /**
     * @return the sessionId if the RequestContext is present
     */
    private Optional<String> getSessionId() {
        return Optional
                .ofNullable(RequestContextHolder.getRequestAttributes())
                .map(RequestAttributes::getSessionId);
    }

    private boolean checkAccess(Authentication auth, ProxySpec spec) {
        if (auth == null || spec == null) {
            return false;
        }

        if (auth instanceof AnonymousAuthenticationToken) {
            // if anonymous -> only allow access if we the backend has no authorization enabled
            return !authBackend.hasAuthorization();
        }

        if (specHasNoAccessControl(spec.getAccessControl())) {
            return true;
        }

        if (allowedByGroups(auth, spec)) {
            return true;
        }

        if (allowedByUsers(auth, spec)) {
            return true;
        }

        if (allowedByExpression(auth, spec)) {
            return true;
        }

        return false;
    }

    public boolean specHasNoAccessControl(ProxyAccessControl accessControl) {
        if (accessControl == null) {
            return true;
        }

        return !accessControl.hasGroupAccess()
                && !accessControl.hasUserAccess()
                && !accessControl.hasExpressionAccess();
    }

    public boolean allowedByGroups(Authentication auth, ProxySpec spec) {
        if (!spec.getAccessControl().hasGroupAccess()) {
            // no groups defined -> this user has no access based on the groups
            return false;
        }
        for (String group : spec.getAccessControl().getGroups()) {
            if (userService.isMember(auth, group)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByUsers(Authentication auth, ProxySpec spec) {
        if (!spec.getAccessControl().hasUserAccess()) {
            // no users defined -> this user has no access based on the users
            return false;
        }
        for (String user : spec.getAccessControl().getUsers()) {
            if (auth.getName().equals(user)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByExpression(Authentication auth, ProxySpec spec) {
        if (!spec.getAccessControl().hasExpressionAccess()) {
            // no expression defined -> this user has no access based on the expression
            return false;
        }
        return false;
    }

}
