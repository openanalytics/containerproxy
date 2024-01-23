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
package eu.openanalytics.containerproxy.backend.dispatcher;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStopReason;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import org.springframework.security.core.Authentication;

public interface IProxyDispatcher {

    public Proxy startProxy(Authentication user, Proxy proxy, ProxySpec spec, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLogBuilder) throws ProxyFailedToStartException;

    public void stopProxy(Proxy proxy, ProxyStopReason proxyStopReason) throws ContainerProxyException;

    public default void stopProxy(Proxy proxy) throws ContainerProxyException {
        stopProxy(proxy, ProxyStopReason.Unknown);
    }

    public void pauseProxy(Proxy proxy);

    public Proxy resumeProxy(Authentication user, Proxy proxy, ProxySpec proxySpec) throws ProxyFailedToStartException;

    public boolean supportsPause();

    public Proxy addRuntimeValuesBeforeSpel(Authentication user, ProxySpec spec, Proxy proxy);
}
