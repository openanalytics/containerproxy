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
package eu.openanalytics.containerproxy.service.session;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.NoAuthenticationBackend;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;

import javax.inject.Inject;

abstract public class AbstractSessionService implements ISessionService {

    @Inject
    @Lazy
    // Note: lazy needed to work around early initialization conflict
    // Only used to check whether we are using Authentication none
    private IAuthenticationBackend authBackend;

    protected String extractAuthName(Authentication authentication, String sessionId) {
        if (authentication != null && !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication != null) {
            return authentication.getName();
        }

        if (authBackend.getName().equals(NoAuthenticationBackend.NAME)) {
            return sessionId;
        }

        return null;
    }

}
