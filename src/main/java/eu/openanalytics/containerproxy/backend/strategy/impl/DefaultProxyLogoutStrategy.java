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
package eu.openanalytics.containerproxy.backend.strategy.impl;

import eu.openanalytics.containerproxy.backend.strategy.IProxyLogoutStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.ProxyService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

/**
 * Default logout behaviour.
 */
@Component
public class DefaultProxyLogoutStrategy implements IProxyLogoutStrategy {

	private static final String PROP_DEFAULT_STOP_PROXIES_ON_LOGOUT = "proxy.default-stop-proxy-on-logout";

	private ProxyService proxyService;

	@Inject
	private Environment environment;

	private boolean defaultStopProxyOnLogout;

	@PostConstruct
	public void init() {
		defaultStopProxyOnLogout = environment.getProperty(PROP_DEFAULT_STOP_PROXIES_ON_LOGOUT, Boolean.class, true);
	}

	@Override
	public void onLogout(String userId) {
		for (Proxy proxy: this.proxyService.getProxies(p -> p.getUserId().equals(userId), true)) {
			if (shouldBeStopped(proxy)) {
				this.proxyService.stopProxy(proxy, true, true);
			}
		}
	}

	public boolean shouldBeStopped(Proxy proxy) {
		if (proxy.getSpec().stopOnLogout() != null) {
			return proxy.getSpec().stopOnLogout();
		}
		return defaultStopProxyOnLogout;
	}

	/**
	 * Setting ProxyService, loosing up circular dependency.
	 *
	 * @param proxyService proxyService.
	 */
	public void setProxyService(ProxyService proxyService) {
		this.proxyService = proxyService;
	}

}
