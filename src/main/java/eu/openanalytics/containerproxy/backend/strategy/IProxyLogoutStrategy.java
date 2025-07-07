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
package eu.openanalytics.containerproxy.backend.strategy;

import org.springframework.security.core.Authentication;

/**
 * Defines a strategy for deciding what to do with a user's proxies when
 * the user logs out.
 */
public interface IProxyLogoutStrategy {

    /**
     * Handles the proxies owned by this user and verifies whether the user can still access them.
     * Only handles proxies for which the spec can be accessed by the current session.
     * @param authentication authentication object of the user
     */
    void onLogout(Authentication authentication);

    /**
     * Handles the proxies owned by this user and verifies whether the user can still access them.
     * Handles all proxies of the user, even if the current session cannot access the spec.
     * @param userId id of the user
     */
    void onLogout(String userId);

}
