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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ProxyAccessControlService {

    private final ProxyService proxyService;

    private final IProxySpecProvider specProvider;

    private final AccessControlEvaluationService accessControlEvaluationService;

    /* This map is used to cache whether a user has access to an app or not.
     * The reason is two-fold:
     *  - for every request made (including static files of apps etc) the access control is checked
     *  - when using the `access-expression` feature, checking the access control means evaluating a SpEL expression
     * I.e. the check can be complex and is performed a lot.
     * This cache uses the SessionId of the user and not the userId for two reasons:
     *  - this ensures that the key is unique
     *  - the roles/properties of a user change when they re-login
     */
    private final Cache<SessionIdAndSpecId, Boolean> authorizationCache;

    public ProxyAccessControlService(ProxyService proxyService, IProxySpecProvider specProvider, AccessControlEvaluationService accessControlEvaluationService) {
        this.proxyService = proxyService;
        this.specProvider = specProvider;
        this.accessControlEvaluationService = accessControlEvaluationService;
        // cache authorization results for (at least) 60 minutes, since this never changes during the lifetime of a session
        authorizationCache = Caffeine.newBuilder()
            .scheduler(Scheduler.systemScheduler())
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .build();
    }

    public boolean canAccess(Authentication auth, String specId) {
        return canAccess(auth, specProvider.getSpec(specId));
    }

    /**
     * @param auth   the current user
     * @param specId the specId the user is trying to access
     * @return whether the user can access the given specId or when this spec does not exist whether the user already
     * has a proxy with this spec id
     */
    public boolean canAccessOrHasExistingProxy(Authentication auth, RequestAuthorizationContext context) {
        if (!context.getVariables().containsKey("specId")) {
            return false;
        }
        String specId = context.getVariables().get("specId");
        ProxySpec spec = specProvider.getSpec(specId);
        if (spec != null) {
            return canAccess(auth, spec);
        }
        return proxyService.getUserProxiesBySpecId(specId).findAny().isPresent();
    }

    public boolean canAccess(Authentication auth, ProxySpec spec) {
        if (auth == null || spec == null) {
            return false;
        }
        Optional<String> sessionId = getSessionId();
        if (sessionId.isEmpty()) {
            return checkAccess(auth, spec);
        }

        // we got a sessionId -> use the cache
        return authorizationCache.get(
            new SessionIdAndSpecId(sessionId.get(), spec.getId()),
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
        return accessControlEvaluationService.checkAccess(auth, spec, spec.getAccessControl());
    }

    private record SessionIdAndSpecId(String userId, String specId) {
    }

}
