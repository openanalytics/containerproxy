/**
 * ShinyProxy
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
package eu.openanalytics.containerproxy.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import eu.openanalytics.containerproxy.ContainerProxyApplication.MappingManager;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.App;
import eu.openanalytics.containerproxy.model.Proxy;
import eu.openanalytics.containerproxy.model.ProxyStatus;
import eu.openanalytics.containerproxy.service.EventService.EventType;
import eu.openanalytics.containerproxy.util.Retrying;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.handlers.ServletRequestContext;

@Service
public class ProxyService {
		
	private Logger log = LogManager.getLogger(ProxyService.class);

	private List<Proxy> activeProxies = Collections.synchronizedList(new ArrayList<>());
	private Map<Proxy, Set<String>> proxySessionIds = Collections.synchronizedMap(new HashMap<>());
	
	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	
	@Inject
	private IContainerBackend backend;

	@Inject
	private MappingManager mappingManager;
	
	@Inject
	private AppService appService;
	
	@Inject
	private EventService eventService;
	
	@PreDestroy
	public void shutdown() {
		containerKiller.shutdown();
		for (Proxy proxy: getProxies(p -> true)) backend.stopProxy(proxy);
	}
	
	public List<Proxy> listProxies() {
		return getProxies(p -> true);
	}
	
	public String getMapping(HttpServletRequest request, String userName, String appName, boolean startNew) {
		waitForLaunchingProxy(userName, appName);
		Proxy proxy = findProxy(userName, appName);
		if (proxy == null && startNew) {
			// The user has no proxy yet.
			proxy = startProxy(userName, appName);
		}
		if (proxy == null) {
			return null;
		} else {
			Set<String> sessionIds = proxySessionIds.get(proxy);
			if (sessionIds == null) {
				sessionIds = new HashSet<>();
				proxySessionIds.put(proxy, sessionIds);
			}
			sessionIds.add(getCurrentSessionId(request));
			return proxy.getName();
		}
	}
	
	public boolean sessionOwnsProxy(HttpServerExchange exchange) {
		String sessionId = getCurrentSessionId(exchange);
		if (sessionId == null) return false;
		
		String proxyName = exchange.getRelativePath();
		return !getProxies(p -> matchesSessionId(p, sessionId) && proxyName.startsWith("/" + p.getName())).isEmpty();
	}
	
	public void releaseProxies(String userName) {
		for (Proxy proxy: getProxies(p -> userName.equals(p.getUserId()))) {
			releaseProxy(proxy, true);
		}
	}
	
	public void releaseProxy(String userName, String appName) {
		Proxy proxy = findProxy(userName, appName);
		if (proxy != null) releaseProxy(proxy, true);
	}
	
	public void releaseProxy(Proxy proxy, boolean async) {
		activeProxies.remove(proxy);
		
		Runnable releaser = () -> {
			try {
				backend.stopProxy(proxy);
				log.info(String.format("Proxy released [user: %s] [app: %s]", proxy.getUserId(), proxy.getApp().getName()));
				eventService.post(EventType.AppStop.toString(), proxy.getUserId(), proxy.getApp().getName());
			} catch (Exception e){
				log.error("Failed to release proxy " + proxy.getName(), e);
			}
		};
		if (async) containerKiller.submit(releaser);
		else releaser.run();
		
		for (Entry<String, URI> target: proxy.getTargets().entrySet()) {
			mappingManager.removeMapping(target.getKey());
		}
	}
	
	private String getCurrentSessionId(HttpServerExchange exchange) {
		if (exchange == null && ServletRequestContext.current() != null) {
			exchange = ServletRequestContext.current().getExchange();
		}
		if (exchange == null) return null;
		Cookie sessionCookie = exchange.getRequestCookies().get("JSESSIONID");
		if (sessionCookie == null) return null;
		return sessionCookie.getValue();
	}

	private String getCurrentSessionId(HttpServletRequest request) {
		if (request == null) {
			return getCurrentSessionId((HttpServerExchange) null);
		}
		javax.servlet.http.Cookie sessionCookie = WebUtils.getCookie(request, "JSESSIONID");
		if (sessionCookie == null) return null;
		return sessionCookie.getValue();
	}
	
	public Proxy startProxy(String userName, String appName) {
		App app = appService.getApp(appName);
		if (app == null) {
			throw new ContainerProxyException("Cannot start container: unknown application: " + appName);
		}
		return startProxy(userName, app);
	}
	
	public Proxy startProxy(String userName, App app) {
		String appName = app.getName();
		if (findProxy(userName, appName) != null) {
			throw new ContainerProxyException("Cannot start container: user " + userName + " already has an active proxy for " + appName);
		}
		
		Proxy proxy = new Proxy();
		proxy.setStatus(ProxyStatus.New);
		proxy.setUserId(userName);
		proxy.setApp(app);
		activeProxies.add(proxy);
		
		try {
			backend.startProxy(proxy);
		} finally {
			if (proxy.getStatus() != ProxyStatus.Up) activeProxies.remove(proxy);
		}
		
		for (Entry<String, URI> target: proxy.getTargets().entrySet()) {
			mappingManager.addMapping(target.getKey(), target.getValue());
		}

		log.info(String.format("Proxy activated [user: %s] [app: %s]", userName, appName));
		eventService.post(EventType.AppStart.toString(), userName, appName);
		
		return proxy;
	}
	
	private void waitForLaunchingProxy(String userName, String appName) {
		int totalWaitMs = 20000; //Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		
		boolean mayProceed = Retrying.retry(i -> {
			return getProxies(p -> p.getStatus() == ProxyStatus.Starting && isUserProxy(p, userName, appName)).isEmpty();
		}, maxTries, waitMs);
		
		if (!mayProceed) throw new ContainerProxyException("Cannot proceed: waiting for proxy to launch");
	}
	
	private Proxy findProxy(String userName, String appName) {
		return getProxies(proxy -> isUserProxy(proxy, userName, appName)).stream().findAny().orElse(null);
	}
	
	private List<Proxy> getProxies(Predicate<Proxy> filter) {
		List<Proxy> matches = new ArrayList<>();
		synchronized (activeProxies) {
			for (Proxy proxy: activeProxies) {
				if (filter.test(proxy)) matches.add(proxy);
			}
		}
		return matches;
	}
	
	private boolean isUserProxy(Proxy proxy, String userId, String appName) {
		return userId.equals(proxy.getUserId()) && appName.equals(proxy.getApp().getName());
	}
	
	private boolean matchesSessionId(Proxy proxy, String sessionId) {
		Set<String> sessionIds = proxySessionIds.get(proxy);
		if (sessionIds == null || sessionIds.isEmpty()) return false;
		return sessionIds.contains(sessionId);
	}

}
