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
package eu.openanalytics.containerproxy.service.session.undertow;

import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.SessionManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "none")
public class CustomSessionManagerFactory implements SessionManagerFactory {

    private InMemorySessionManager inMemorySessionManager = null;

    @Override
    public SessionManager createSessionManager(Deployment deployment) {
        if (inMemorySessionManager == null) {
            inMemorySessionManager = new InMemorySessionManager(
                    deployment.getDeploymentInfo().getSessionIdGenerator(),
                    deployment.getDeploymentInfo().getDeploymentName(),
                    -1,
                    false,
                    deployment.getDeploymentInfo().getMetricsCollector() != null);
        }
        return inMemorySessionManager;
    }

    public InMemorySessionManager getInstance() {
        return inMemorySessionManager;
    }

}
