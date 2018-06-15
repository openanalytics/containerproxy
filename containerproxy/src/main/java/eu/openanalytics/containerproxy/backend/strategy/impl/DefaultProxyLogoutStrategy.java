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
