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
package eu.openanalytics.containerproxy.backend.strategy.impl;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.backend.strategy.IProxyLogoutStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.ProxyService;

/**
 * Default logout behaviour: stop all proxies owned by the user.
 */
@Component
public class DefaultProxyLogoutStrategy implements IProxyLogoutStrategy {

	@Inject
	private ProxyService proxyService;
	
	@Override
	public void onLogout(String userId) {
		for (Proxy proxy: proxyService.getProxies(p -> p.getUserId().equals(userId), true)) {
			proxyService.stopProxy(proxy, true, true);
		}
	}

}
