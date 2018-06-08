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
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.EventService.EventType;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;

@Service
public class ProxyService {
		
	private Logger log = LogManager.getLogger(ProxyService.class);
	private List<Proxy> activeProxies = Collections.synchronizedList(new ArrayList<>());
	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	
	@Inject
	private IContainerBackend backend;

	@Inject
	private ProxyMappingManager mappingManager;
	
	@Inject
	private UserService userService;
	
	@Inject
	private EventService eventService;
	
	@PreDestroy
	public void shutdown() {
		containerKiller.shutdown();
		for (Proxy proxy: getProxies(p -> true)) backend.stopProxy(proxy);
	}
	
	public Proxy getProxy(String id) {
		return getProxies(proxy -> proxy.getId().equals(id)).stream().findAny().orElse(null);
	}
	
	public List<Proxy> getProxies(String userId) {
		if (userId == null) return Collections.emptyList();
		return getProxies(proxy -> userId.equals(proxy.getUserId()));
	}
	
	public List<Proxy> listActiveProxies() {
		return getProxies(p -> true);
	}
	
	public Proxy startProxy(ProxySpec spec) {
		if (!userService.canAccess(spec)) {
			throw new AccessDeniedException(String.format("Cannot start proxy %s: access denied", spec.getId()));
		}
		
		Proxy proxy = new Proxy();
		proxy.setStatus(ProxyStatus.New);
		proxy.setUserId(userService.getCurrentUserId());
		proxy.setSpec(spec);
		activeProxies.add(proxy);
		
		try {
			backend.startProxy(proxy);
		} finally {
			if (proxy.getStatus() != ProxyStatus.Up) activeProxies.remove(proxy);
		}
		
		for (Entry<String, URI> target: proxy.getTargets().entrySet()) {
			mappingManager.addMapping(target.getKey(), target.getValue());
		}

		log.info(String.format("Proxy activated [user: %s] [proxy: %s]", proxy.getUserId(), spec.getId()));
		eventService.post(EventType.ProxyStart.toString(), proxy.getUserId(), spec.getId());
		
		return proxy;
	}
	
	public void stopProxy(String proxyId) {
		Proxy proxy = getProxy(proxyId);
		if (proxy != null) stopProxy(proxy, true);
	}

	public void stopProxy(Proxy proxy, boolean async) {
		activeProxies.remove(proxy);
		
		Runnable releaser = () -> {
			try {
				backend.stopProxy(proxy);
				log.info(String.format("Proxy released [user: %s] [proxy: %s]", proxy.getUserId(), proxy.getSpec().getId()));
				eventService.post(EventType.ProxyStop.toString(), proxy.getUserId(), proxy.getSpec().getId());
			} catch (Exception e){
				log.error("Failed to release proxy " + proxy.getId(), e);
			}
		};
		if (async) containerKiller.submit(releaser);
		else releaser.run();
		
		for (Entry<String, URI> target: proxy.getTargets().entrySet()) {
			mappingManager.removeMapping(target.getKey());
		}
	}
	
	private List<Proxy> getProxies(Predicate<Proxy> filter) {
		boolean isAdmin = userService.isAdmin();
		List<Proxy> matches = new ArrayList<>();
		synchronized (activeProxies) {
			for (Proxy proxy: activeProxies) {
				boolean isOwner = userService.isOwner(proxy);
				if (filter.test(proxy) && (isOwner || isAdmin)) matches.add(proxy);
			}
		}
		return matches;
	}

}
