/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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
package eu.openanalytics.containerproxy.session.undertow;

import eu.openanalytics.containerproxy.session.ISessionInformation;
import eu.openanalytics.containerproxy.event.UserLogoutEvent;
import io.undertow.server.HttpServerExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;

@Component
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "none")
public class UndertowSessionInformation implements ISessionInformation {

    @Inject
    private CustomSessionManagerFactory customInMemorySessionManagerFactory;

//    @Override
//    public Session findById(String sessionId) {
//        return null;
//    }

    // This map keeps track of the logged-in users and their session in order to give the correct amount of logged in
    // users.
    private final HashMap<String, HashSet<String>> usersToSessionId = new HashMap<>();

    @Override
    public Long getLoggedInUsersCount() {
        return usersToSessionId.values().stream().filter(v -> !v.isEmpty()).count();
    }

    @Override
    public void reActivateSession(String sessionId) {
        io.undertow.server.session.Session session = customInMemorySessionManagerFactory.getInstance().getSession(sessionId);
        if (session != null) {
            session.requestDone(new HttpServerExchange(null));
        }
    }


    @EventListener
    public void onUserSessionLogoutEvent(UserLogoutEvent event) {
        synchronized (usersToSessionId) {
            if (usersToSessionId.containsKey(event.getUserId())) {
                usersToSessionId.get(event.getUserId()).remove(event.getSessionId());
            }
        }
    }

    @EventListener
    public void onSessionIdChangeEvent(SessionFixationProtectionEvent event) {
        String userId = event.getAuthentication().getName(); // TODO anonymous
        synchronized (usersToSessionId) {
            if (usersToSessionId.containsKey(userId)) {
                usersToSessionId.get(userId).remove(event.getOldSessionId());
                usersToSessionId.get(userId).add(event.getNewSessionId());
            }
        }
    }

    @EventListener
    public void onAuthenticationSuccessEvent(AuthenticationSuccessEvent event) {
        String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();
        String userId = event.getAuthentication().getName(); // TODO anonymous access
        synchronized (usersToSessionId) {
            if (!usersToSessionId.containsKey(userId)) {
                usersToSessionId.put(userId, new HashSet<>());
            }
            usersToSessionId.get(userId).add(sessionId);
        }
    }



}
