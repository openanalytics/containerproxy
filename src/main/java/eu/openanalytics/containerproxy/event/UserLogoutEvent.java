/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
package eu.openanalytics.containerproxy.event;

import org.springframework.context.ApplicationEvent;
import org.springframework.security.core.Authentication;

public class UserLogoutEvent extends ApplicationEvent {

    private final String userId;
    private final Boolean wasExpired;
    private final Authentication authentication;

    /**
     * @param source
     * @param userId
     * @param wasExpired whether the user is logged automatically because the session has expired
     */
    public UserLogoutEvent(Object source, String userId, Boolean wasExpired, Authentication authentication) {
        super(source);
        this.userId = userId;
        this.wasExpired = wasExpired;
        this.authentication = authentication;
    }

    public String getUserId() {
        return userId;
    }

    public Boolean getWasExpired() {
        return wasExpired;
    }

    public Authentication getAuthentication() {
        return authentication;
    }
}
