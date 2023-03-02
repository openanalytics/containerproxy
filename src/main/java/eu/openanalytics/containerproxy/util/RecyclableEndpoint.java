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
package eu.openanalytics.containerproxy.util;

import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.hearbeat.WebSocketCounterService;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@WebEndpoint(id = "recyclable")
public class RecyclableEndpoint {

    @Inject
    private WebSocketCounterService webSocketCounterService;

    @Inject
    private ProxyService proxyService;

    @ReadOperation
    public Map<String, Object> health() {
        boolean proxyActionInProgress = proxyService.isBusy();
        int count = webSocketCounterService.getCount();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("isRecyclable", count == 0 && !proxyActionInProgress);
        details.put("activeConnections", count);
        return details;
    }

}
