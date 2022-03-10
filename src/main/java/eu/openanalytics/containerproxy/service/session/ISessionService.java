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

import io.undertow.server.HttpServerExchange;

/**
 * Service to manage/query the session of users.
 */
public interface ISessionService {

    /**
     * @return the current amount of users logged in. This value may internally be cached and therefore the exact
     * value can be delayed.
     */
    public Integer getLoggedInUsersCount();

    /**
     * @return the current amount of active users. This value may internally be cached and therefore the exact
     * value can be delayed.
     */
    public Integer getActiveUsersCount();

    /**
     * Re-activates the session of the given sessionId. This means that the last-active time is set to the current time.
     * @param sessionId the session to update
     */
    public void reActivateSession(String sessionId);

    /**
     * Finds the sessionId of the user in the given exchange. Does not use any context (e.g. RequestContext) and therefore
     * can be used outside such a context.
     * @param exchange the exchange to extract the sessionId from
     * @return the sessionId
     */
    public String extractSessionIdFromExchange(HttpServerExchange exchange);

}
