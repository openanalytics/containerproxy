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
package eu.openanalytics.containerproxy.util;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.service.HeartbeatService;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.AttachmentKey;
import io.undertow.util.PathMatcher;

/**
 * This component keeps track of which proxy mappings (i.e. URL endpoints) are currently registered,
 * and tells Undertow where they should proxy to.
 */
@Component
public class ProxyMappingManager {

	private static final String PROXY_INTERNAL_ENDPOINT = "/proxy_endpoint";
	private static final AttachmentKey<ProxyMappingManager> ATTACHMENT_KEY_DISPATCHER = AttachmentKey.create(ProxyMappingManager.class);
	
	private PathHandler pathHandler;
	
	private Map<String, String> mappings = new HashMap<>();
	
	@Inject
	private HeartbeatService heartbeatService;
	
	public synchronized HttpHandler createHttpHandler(HttpHandler defaultHandler) {
		if (pathHandler == null) {
			pathHandler = new ProxyPathHandler(defaultHandler);
		}
		return pathHandler;
	}
	
	@SuppressWarnings("deprecation")
	public synchronized void addMapping(String proxyId, String mapping, URI target) {
		if (pathHandler == null) throw new IllegalStateException("Cannot change mappings: web server is not yet running.");
		
		LoadBalancingProxyClient proxyClient = new LoadBalancingProxyClient() {
			@Override
			public void getConnection(ProxyTarget target, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
				try {
					exchange.addResponseCommitListener(ex -> heartbeatService.attachHeartbeatChecker(ex, proxyId));
				} catch (Exception e) {
					e.printStackTrace();
				}
				super.getConnection(target, exchange, callback, timeout, timeUnit);
			}
		};
		proxyClient.setMaxQueueSize(100);
		proxyClient.addHost(target);

		mappings.put(mapping, proxyId);
		
		String path = PROXY_INTERNAL_ENDPOINT + "/" + mapping;
		pathHandler.addPrefixPath(path, new ProxyHandler(proxyClient, ResponseCodeHandler.HANDLE_404));
	}

	public synchronized void removeMapping(String mapping) {
		if (pathHandler == null) throw new IllegalStateException("Cannot change mappings: web server is not yet running.");
		mappings.remove(mapping);
		pathHandler.removePrefixPath(mapping);
	}

	public String getProxyId(String mapping) {
		for (Entry<String,String> e: mappings.entrySet()) {
			if (mapping.toLowerCase().startsWith(e.getKey().toLowerCase())) return e.getValue();
		}
		return null;
	}

	/**
	 * Dispatch a request to a target proxy mapping.
	 * 
	 * This approach should be used to dispatch requests from a Spring-secured servlet context
	 * to an unsecured Undertow handler.
	 * 
	 * Note that clients can never access a proxy handler directly (for security reasons).
	 * Dispatching is the only allowed method to access proxy handlers.
	 * 
	 * @param mapping The target mapping to dispatch to.
	 * @param request The request to dispatch.
	 * @param response The response corresponding to the request.
	 * @throws IOException If the dispatch fails for an I/O reason.
	 * @throws ServletException If the dispatch fails for any other reason.
	 */
	public void dispatchAsync(String mapping, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		HttpServerExchange exchange = ServletRequestContext.current().getExchange();
		exchange.putAttachment(ATTACHMENT_KEY_DISPATCHER, this);
		
		String queryString = request.getQueryString();
		queryString = (queryString == null) ? "" : "?" + queryString;
		String targetPath = PROXY_INTERNAL_ENDPOINT + "/" + mapping + queryString;
		
		request.startAsync();
		request.getRequestDispatcher(targetPath).forward(request, response);
	}
	
	private static class ProxyPathHandler extends PathHandler {
		
		public ProxyPathHandler(HttpHandler defaultHandler) {
			super(defaultHandler);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void handleRequest(HttpServerExchange exchange) throws Exception {
			Field field = PathHandler.class.getDeclaredField("pathMatcher");
			field.setAccessible(true);
			PathMatcher<HttpHandler> pathMatcher = (PathMatcher<HttpHandler>) field.get(this);
			PathMatcher.PathMatch<HttpHandler> match = pathMatcher.match(exchange.getRelativePath());

			// Note: this handler may never be accessed directly (because it bypasses Spring security).
			// Only allowed if the request was dispatched via this class.
			if (match.getValue() instanceof ProxyHandler && exchange.getAttachment(ATTACHMENT_KEY_DISPATCHER) == null) {
				exchange.setStatusCode(403);
				exchange.getResponseChannel().write(ByteBuffer.wrap("Not authorized to access this proxy".getBytes()));
			} else {
				super.handleRequest(exchange);
			}
		}
	}
}
