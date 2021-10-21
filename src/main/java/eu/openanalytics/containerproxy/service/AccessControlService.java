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
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccessControlService {

    private final IAuthenticationBackend authBackend;
    private final UserService userService;
    private final IProxySpecProvider specProvider;
    private final SpecExpressionResolver specExpressionResolver;

    /* This map is used to cache whether a user has access to an app or not.
     * The reason is two-fold:
     *  - for every request made (including static files of apps etc) the access control is checked
     *  - when using the `access-expression` feature, checking the access control means evaluating a SpEL expression
     * I.e. the check can be complex and is performed a lot.
     * This cache uses the SessionId of the user and not the userId for two reasons:
     *  - this ensures that the key is unique
     *  - the roles/properties of a user change when they re-login
     */
    private final Map<Pair<String, String>, Boolean> authorizationCache = new ConcurrentHashMap<>();

    public AccessControlService(@Lazy IAuthenticationBackend authBackend, UserService userService, IProxySpecProvider specProvider, SpecExpressionResolver specExpressionResolver) {
        this.authBackend = authBackend;
        this.userService = userService;
        this.specProvider = specProvider;
        this.specExpressionResolver = specExpressionResolver;
    }

    public boolean canAccess(Authentication auth, String specId) {
        return canAccess(auth, specProvider.getSpec(specId));
    }

    public boolean canAccess(Authentication auth, ProxySpec spec) {
        if (auth == null || spec == null) {
            return false;
        }
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
        SpecExpressionContext context = SpecExpressionContext.create(auth, auth.getPrincipal(), auth.getCredentials(), spec);
        return specExpressionResolver.evaluateToBoolean(spec.getAccessControl().getExpression(), context);
    }

    @EventListener
    public void onSessionDestroyedEvent(HttpSessionDestroyedEvent event) {
        // remove all entries in cache for this sessionId
        authorizationCache.keySet().removeIf(it -> it.getLeft().equals(event.getId()));
    }

}
