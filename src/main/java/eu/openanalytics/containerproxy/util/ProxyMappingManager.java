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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.service.AsyncProxyService;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import io.undertow.io.Sender;
import io.undertow.server.DefaultResponseListener;
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
import io.undertow.util.Headers;
import io.undertow.util.PathMatcher;
import org.springframework.context.annotation.Lazy;
import io.undertow.util.StatusCodes;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.header.Header;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This component keeps track of which proxy mappings (i.e. URL endpoints) are currently registered,
 * and tells Undertow where they should proxy to.
 */
@Component
public class ProxyMappingManager {

	private static final String PROXY_INTERNAL_ENDPOINT = "/proxy_endpoint";
	private static final AttachmentKey<ProxyMappingManager> ATTACHMENT_KEY_DISPATCHER = AttachmentKey.create(ProxyMappingManager.class);
	private static final AttachmentKey<ProxyIdAttachment> ATTACHMENT_KEY_PROXY_ID = AttachmentKey.create(ProxyIdAttachment.class);

	private final StructuredLogger logger = StructuredLogger.create(getClass());

	private PathHandler pathHandler;
	
	private final Map<String, String> mappings = new HashMap<>();

	private volatile boolean isShuttingDown = false;

	@Inject
	@Lazy
	private HeartbeatService heartbeatService;

	@Inject
	@Lazy
	private ProxyService proxyService;

	@Inject
	@Lazy
	private AsyncProxyService asyncProxyService;

	public synchronized HttpHandler createHttpHandler(HttpHandler defaultHandler) {
		if (pathHandler == null) {
			pathHandler = new ProxyPathHandler(defaultHandler);
		}
		return pathHandler;
	}
	
	@SuppressWarnings("deprecation")
	public synchronized void addMapping(String proxyId, String mapping, URI target) {
		if (pathHandler == null) throw new IllegalStateException("Cannot change mappings: web server is not yet running.");

		if (mappings.containsKey(mapping)) {
			return;
		}

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
	 * @param proxyId The id of the proxy
	 * @param mapping The target mapping to dispatch to.
	 * @param request The request to dispatch.
	 * @param response The response corresponding to the request.
	 * @throws IOException If the dispatch fails for an I/O reason.
	 * @throws ServletException If the dispatch fails for any other reason.
	 */
	public void dispatchAsync(String proxyId, String mapping, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		dispatchAsync(proxyId, mapping, request, response, null);
	}

	public void dispatchAsync(String proxyId, String mapping, HttpServletRequest request, HttpServletResponse response, Consumer<HttpServerExchange> exchangeCustomizer) throws IOException, ServletException {
		HttpServerExchange exchange = ServletRequestContext.current().getExchange();
		exchange.putAttachment(ATTACHMENT_KEY_DISPATCHER, this);
		exchange.putAttachment(ATTACHMENT_KEY_PROXY_ID, new ProxyIdAttachment(proxyId));
		
		String queryString = request.getQueryString();
		queryString = (queryString == null) ? "" : "?" + queryString;
		String targetPath = PROXY_INTERNAL_ENDPOINT + "/" + mapping + queryString;

		if (exchangeCustomizer != null) {
			exchangeCustomizer.accept(exchange);
		}
		// see #31010
		// by default Undertow adds a Headers.X_FORWARDED_HOST header using exchange.getHost(), this never includes the server port
		// however, the original Host header includes the port if using a non-standard port (i.e. not 80 and 443)
		// this causes problems for applications comparing the Host and/or Origin header
		// therefore we set the header here using exchange.getHostAndPort(), which only includes the port if non-standard port such that it matches the Host header
		// note: if we set the header here, undertow does not override it
		exchange.getRequestHeaders().put(Headers.X_FORWARDED_HOST, exchange.getHostAndPort());
		exchange.addDefaultResponseListener(defaultResponseListener);
		request.startAsync();
		request.getRequestDispatcher(targetPath).forward(request, response);
	}

	@EventListener(ContextClosedEvent.class)
	public void onApplicationEvent(ContextClosedEvent event) {
		isShuttingDown = true;
	}

	private final DefaultResponseListener defaultResponseListener = responseExchange -> {
		// note: if ShinyProxy was restarted it can take up to one minute for the request to timeout/fail
		if (!responseExchange.isResponseChannelAvailable()) {
			return false;
		}
		if (responseExchange.getStatusCode() == StatusCodes.SERVICE_UNAVAILABLE) {
			ProxyIdAttachment proxyIdAttachment = responseExchange.getAttachment(ATTACHMENT_KEY_PROXY_ID);
			Proxy proxy = null;
			if (proxyIdAttachment != null) {
				try {
					proxy = proxyService.getProxy(proxyIdAttachment.proxyId);
					if (proxy != null && !proxy.getStatus().isUnavailable() && !isShuttingDown) {
						logger.info(proxy, "Proxy unreachable/crashed, stopping it now");
						asyncProxyService.stopProxy(proxy, true);
					}
				} catch (Throwable t) {
					// ignore in order to complete request
				}
			}

			String errorPage;
			if (proxy != null && proxy.getStatus() != ProxyStatus.Stopped) {
				errorPage = "{\"status\":\"error\", \"message\":\"app_crashed\"}";
			} else {
				// in-progress request got terminated because the app has been stopped (not crashed)
				errorPage = "{\"status\":\"error\", \"message\":\"app_stopped_or_non_existent\"}";
			}
			responseExchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + errorPage.length());
			responseExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
			Sender sender = responseExchange.getResponseSender();
			sender.send(errorPage);
			return true;
		}
		return false;
	};
	
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

	private static class ProxyIdAttachment {
		final String proxyId;

		public ProxyIdAttachment(String proxyId) {
			this.proxyId = proxyId;
		}
	}

}
