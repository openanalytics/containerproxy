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
package eu.openanalytics.containerproxy.service.hearbeat;

import eu.openanalytics.containerproxy.service.session.ISessionService;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Service which updates the "last active time" of a Session when an Websocket heartbeat is received.
 * The idea is to extend the lifetime of these session whenever a websocket connection related to that session is active.
 */
@Service
public class SessionReActivatorService implements IHeartbeatProcessor {

    @Inject
    private ISessionService sessionService;

    @Override
    public void heartbeatReceived(@Nonnull HeartbeatService.HeartbeatSource heartbeatSource, @Nonnull String proxyId, @Nullable String sessionId) {
        if (heartbeatSource != HeartbeatService.HeartbeatSource.WEBSOCKET_PONG || sessionId == null) {
            return;
        }

        sessionService.reActivateSession(sessionId);
    }
}
