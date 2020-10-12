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
package eu.openanalytics.containerproxy.service;

import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import eu.openanalytics.containerproxy.util.ProxyMappingManager;

/**
 * <p>
 * This service is the entry point for working with proxies.
 * It offers methods to list, start and stop proxies, as well
 * as methods for managing proxy specs.
 * </p><p>
 * A note about security: these methods are considered internal API,
 * and are therefore allowed to bypass security checks.<br/>
 * The caller is always responsible for performing security
 * checks before manipulating proxies.
 * </p>
 */
@Service
public class ProxyService {
		
	private Logger log = LogManager.getLogger(ProxyService.class);
	private List<Proxy> activeProxies = Collections.synchronizedList(new ArrayList<>());
	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	
	@Inject
	private IProxySpecProvider baseSpecProvider;
	
	@Inject
	private IProxySpecMergeStrategy specMergeStrategy;
	
	@Inject
	private IContainerBackend backend;

	@Inject
	private ProxyMappingManager mappingManager;
	
	@Inject
	private UserService userService;
	
	@Inject
	private EventService eventService;
	
	@Inject
	private LogService logService;
	
	@PreDestroy
	public void shutdown() {
		containerKiller.shutdown();
		for (Proxy proxy: getProxies(null, true)) backend.stopProxy(proxy);
	}
	
	/**
	 * Find the ProxySpec that matches the given ID.
	 * 
	 * @param id The ID to look for.
	 * @return A matching ProxySpec, or null if no match was found.
	 */
	public ProxySpec getProxySpec(String id) {
		if (id == null || id.isEmpty()) return null;
		return findProxySpec(spec -> spec.getId().equals(id), true);
	}
	
	/**
	 * Find the first ProxySpec that matches the given filter.
	 * 
	 * @param filter The filter to match, may be null.
	 * @param ignoreAccessControl True to search in all ProxySpecs, regardless of the current security context.
	 * @return The first ProxySpec found that matches the filter, or null if no match was found.
	 */
	public ProxySpec findProxySpec(Predicate<ProxySpec> filter, boolean ignoreAccessControl) {
		return getProxySpecs(filter, ignoreAccessControl).stream().findAny().orElse(null);
	}
	
	/**
	 * Find all ProxySpecs that match the given filter.
	 * 
	 * @param filter The filter to match, or null.
	 * @param ignoreAccessControl True to search in all ProxySpecs, regardless of the current security context.
	 * @return A List of matching ProxySpecs, may be empty.
	 */
	public List<ProxySpec> getProxySpecs(Predicate<ProxySpec> filter, boolean ignoreAccessControl) {
		return baseSpecProvider.getSpecs().stream()
				.filter(spec -> ignoreAccessControl || userService.canAccess(spec))
				.filter(spec -> filter == null || filter.test(spec))
				.collect(Collectors.toList());
	}
	
	/**
	 * Resolve a ProxySpec. A base spec will be merged with a runtime spec (one of them is optional),
	 * and an optional set of runtime settings will be applied to the resulting spec.
	 * 
	 * @param baseSpec The base spec, provided by the configured {@link IProxySpecProvider}.
	 * @param runtimeSpec The runtime spec, may be null if <b>baseSpec</b> is not null.
	 * @param runtimeSettings Optional runtime settings.
	 * @return A merged ProxySpec that can be used to launch new proxies.
	 * @throws ProxySpecException If the merge fails for any reason.
	 * @see IProxySpecMergeStrategy
	 */
	public ProxySpec resolveProxySpec(ProxySpec baseSpec, ProxySpec runtimeSpec, Set<RuntimeSetting> runtimeSettings) throws ProxySpecException {
		return specMergeStrategy.merge(baseSpec, runtimeSpec, runtimeSettings);
	}
	
	/**
	 * Find a proxy using its ID.
	 * 
	 * @param id The ID of the proxy to find.
	 * @return The matching proxy, or null if no match was found.
	 */
	public Proxy getProxy(String id) {
		return findProxy(proxy -> proxy.getId().equals(id), true);
	}
	
	/**
	 * Find The first proxy that matches the given filter.
	 * 
	 * @param filter The filter to apply while searching, or null.
	 * @param ignoreAccessControl True to search in all proxies, regardless of the current security context.
	 * @return The first proxy found that matches the filter, or null if no match was found.
	 */
	public Proxy findProxy(Predicate<Proxy> filter, boolean ignoreAccessControl) {
		return getProxies(filter, ignoreAccessControl).stream().findAny().orElse(null);
	}
	
	/**
	 * Find all proxies that match an optional filter.
	 * 
	 * @param filter The filter to match, or null.
	 * @param ignoreAccessControl True to search in all proxies, regardless of the current security context.
	 * @return A List of matching proxies, may be empty.
	 */
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
	
	/**
	 * Launch a new proxy using the given ProxySpec.
	 * 
	 * @param spec The ProxySpec to base the new proxy on.
	 * @param ignoreAccessControl True to allow access to the given ProxySpec, regardless of the current security context.
	 * @return The newly launched proxy.
	 * @throws ContainerProxyException If the proxy fails to start for any reason.
	 */
	public Proxy startProxy(ProxySpec spec, boolean ignoreAccessControl) throws ContainerProxyException {
		if (!ignoreAccessControl && !userService.canAccess(spec)) {
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
			mappingManager.addMapping(proxy.getId(), target.getKey(), target.getValue());
		}

		if (logService.isLoggingEnabled()) {
			BiConsumer<OutputStream, OutputStream> outputAttacher = backend.getOutputAttacher(proxy);
			if (outputAttacher == null) {
				log.warn("Cannot log proxy output: " + backend.getClass() + " does not support output attaching.");
			} else {
				logService.attachToOutput(proxy, outputAttacher);
			}
		}
		
		log.info(String.format("Proxy activated [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), spec.getId(), proxy.getId()));
		eventService.post(EventType.ProxyStart.toString(), proxy.getUserId(), spec.getId());
		
		return proxy;
	}

	/**
	 * Stop a running proxy.
	 * 
	 * @param proxy The proxy to stop.
	 * @param async True to return immediately and stop the proxy in an asynchronous manner.
	 * @param ignoreAccessControl True to allow access to any proxy, regardless of the current security context.
	 */
	public void stopProxy(Proxy proxy, boolean async, boolean ignoreAccessControl) {
		if (!ignoreAccessControl && !userService.isAdmin() && !userService.isOwner(proxy)) {
			throw new AccessDeniedException(String.format("Cannot stop proxy %s: access denied", proxy.getId()));
		}
		
		activeProxies.remove(proxy);
		
		Runnable releaser = () -> {
			try {
				backend.stopProxy(proxy);
				logService.detach(proxy);
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
