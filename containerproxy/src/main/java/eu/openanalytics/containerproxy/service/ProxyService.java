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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.EventService.EventType;
import eu.openanalytics.containerproxy.spec.IProxySpecMergeStrategy;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.ProxySpecException;
import eu.openanalytics.containerproxy.spec.impl.DefaultSpecMergeStrategy;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;

@Service
public class ProxyService {
		
	private Logger log = LogManager.getLogger(ProxyService.class);
	private List<Proxy> activeProxies = Collections.synchronizedList(new ArrayList<>());
	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	
	@Inject
	private IProxySpecProvider baseSpecProvider;
	
	@Autowired(required=false)
	private IProxySpecMergeStrategy specMergeStrategy = new DefaultSpecMergeStrategy();
	
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
		for (Proxy proxy: getProxies(null, true)) backend.stopProxy(proxy);
	}
	
	public ProxySpec getProxySpec(String id) {
		return findProxySpec(spec -> spec.getId().equals(id), true);
	}
	
	public ProxySpec resolveProxySpec(ProxySpec baseSpec, ProxySpec runtimeSpec, Set<RuntimeSetting> runtimeSettings) throws ProxySpecException {
		return specMergeStrategy.merge(baseSpec, runtimeSpec, runtimeSettings);
	}
	
	public Proxy getProxy(String id) {
		return findProxy(proxy -> proxy.getId().equals(id), true);
	}
	
	public Proxy findProxy(Predicate<Proxy> filter, boolean ignoreAccessControl) {
		return getProxies(filter, ignoreAccessControl).stream().findAny().orElse(null);
	}
	
	public List<Proxy> getProxies(Predicate<Proxy> filter, boolean ignoreAccessControl) {
		boolean isAdmin = userService.isAdmin();
		List<Proxy> matches = new ArrayList<>();
		synchronized (activeProxies) {
			for (Proxy proxy: activeProxies) {
				boolean hasAccess = ignoreAccessControl || isAdmin || userService.isOwner(proxy);
				if (hasAccess && (filter == null || filter.test(proxy))) matches.add(proxy);
			}
		}
		return matches;
	}
	
	public ProxySpec findProxySpec(Predicate<ProxySpec> filter, boolean ignoreAccessControl) {
		return getProxySpecs(filter, ignoreAccessControl).stream().findAny().orElse(null);
	}
	
	public List<ProxySpec> getProxySpecs(Predicate<ProxySpec> filter, boolean ignoreAccessControl) {
		return baseSpecProvider.getSpecs().stream()
				.filter(spec -> ignoreAccessControl || userService.canAccess(spec))
				.filter(spec -> filter == null || filter.test(spec))
				.collect(Collectors.toList());
	}
	
	public Proxy startProxy(ProxySpec spec) throws ContainerProxyException {
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

		log.info(String.format("Proxy activated [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), spec.getId(), proxy.getId()));
		eventService.post(EventType.ProxyStart.toString(), proxy.getUserId(), spec.getId());
		
		return proxy;
	}

	public void stopProxy(Proxy proxy, boolean async) {
		if (!userService.isAdmin() && !userService.isOwner(proxy)) {
			throw new AccessDeniedException(String.format("Cannot stop proxy %s: access denied", proxy.getId()));
		}
		
		activeProxies.remove(proxy);
		
		Runnable releaser = () -> {
			try {
				backend.stopProxy(proxy);
				log.info(String.format("Proxy released [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpec().getId(), proxy.getId()));
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

}
