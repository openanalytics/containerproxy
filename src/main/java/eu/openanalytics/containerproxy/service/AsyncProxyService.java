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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStopReason;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.ExecutorServiceFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Service
public class AsyncProxyService {

    private final ExecutorService executor = ExecutorServiceFactory.create("AsyncProxyService");

    @Inject
    private ProxyService proxyService;

    @Inject
    private UserService userService;

    public Proxy startProxy(ProxySpec spec, List<RuntimeValue> runtimeValues, String proxyId, Map<String, String> parameters) {
        Authentication user = userService.getCurrentAuth();
        ProxyService.Command command = proxyService.startProxy(user, spec, runtimeValues, proxyId, parameters);
        executor.submit(command);
        return proxyService.getProxy(proxyId);
    }

    public void stopProxy(Proxy proxy, boolean ignoreAccessControl) {
        stopProxy(proxy, ignoreAccessControl, ProxyStopReason.Unknown);
    }

    public void stopProxy(Proxy proxy, boolean ignoreAccessControl, ProxyStopReason proxyStopReason) {
        Authentication user = userService.getCurrentAuth();
        ProxyService.Command command = proxyService.stopProxy(user, proxy, ignoreAccessControl, proxyStopReason);
        executor.submit(command);
    }

    public void pauseProxy(Proxy proxy, boolean ignoreAccessControl) {
        Authentication user = userService.getCurrentAuth();
        ProxyService.Command command = proxyService.pauseProxy(user, proxy, ignoreAccessControl);
        executor.submit(command);
    }

    public void resumeProxy(Proxy proxy, Map<String, String> parameters) {
        // access control check and state change must happen synchronously
        Authentication user = userService.getCurrentAuth();
        ProxyService.Command command = proxyService.resumeProxy(user, proxy, parameters);
        executor.submit(command);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

}
