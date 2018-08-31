/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.service.HeartbeatService;
import eu.openanalytics.containerproxy.util.SessionHelper.SessionOwnerInfo;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * This component keeps track of which proxy mappings (i.e. URL endpoints) are currently registered,
 * and tells Undertow where they should proxy to.
 * 
 * It can also perform a check to ensure that a request for a proxy URL is authorized.
 * Since the (Undertow) proxy handler does not invoke any Spring security filters, we must
 * perform this check ourselves.
 */
@Component
public class ProxyMappingManager {

	private PathHandler pathHandler;
	
	private Map<String, SessionOwnerInfo> ownerInfo = Collections.synchronizedMap(new HashMap<>());
	
	@Inject
	private HeartbeatService heartbeatService;
	
	@SuppressWarnings("deprecation")
	public synchronized void addMapping(String proxyId, String path, URI target) {
		if (pathHandler == null) throw new IllegalStateException("Cannot change mappings: web server is not yet running.");
		
		LoadBalancingProxyClient proxyClient = new LoadBalancingProxyClient() {
			@Override
			public void getConnection(ProxyTarget target, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
				super.getConnection(target, exchange, callback, timeout, timeUnit);
				exchange.addResponseCommitListener(ex -> heartbeatService.attachHeartbeatChecker(ex, proxyId));
			}
		};
		proxyClient.addHost(target);
		pathHandler.addPrefixPath(path, new ProxyHandler(proxyClient, ResponseCodeHandler.HANDLE_404));
		
		HttpServerExchange exchange = ServletRequestContext.current().getExchange();
		ownerInfo.put(path, SessionHelper.createOwnerInfo(exchange));
	}

	public synchronized void removeMapping(String path) {
		if (pathHandler == null) throw new IllegalStateException("Cannot change mappings: web server is not yet running.");
		pathHandler.removePrefixPath(path);
		ownerInfo.remove(path);
	}

	public boolean requestHasAccess(HttpServerExchange exchange) {
		SessionOwnerInfo exchangeOwner = SessionHelper.createOwnerInfo(exchange);
		String exchangeMapping = exchange.getRelativePath();

		synchronized (ownerInfo) {
			for (String mapping: ownerInfo.keySet()) {
				if (exchangeMapping.startsWith("/" + mapping)) {
					return ownerInfo.get(mapping).isSame(exchangeOwner);
				}
			}
		}

		// This request doesn't go to any known proxy mapping.
		return true;
	}
	
	public void setPathHandler(PathHandler pathHandler) {
		this.pathHandler = pathHandler;
	}
}
