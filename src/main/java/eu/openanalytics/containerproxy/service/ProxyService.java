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
package eu.openanalytics.containerproxy.service;

import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.store.IActiveProxies;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
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

	@Inject
	private IActiveProxies activeProxies;
	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	
	@Inject
	private IProxySpecProvider baseSpecProvider;
	
	@Inject
	private IContainerBackend backend;

	@Inject
	private ProxyMappingManager mappingManager;
	
	@Inject
	private UserService userService;
	
	@Inject
	private LogService logService;

	@Inject
	private ApplicationEventPublisher applicationEventPublisher;

	@Inject
	private Environment environment;

	@Inject
	private RuntimeValueService runtimeValueService;

	@Inject
	private ProxyStatusService proxyStatusService;

	@Inject
	private SpecExpressionResolver expressionResolver;

	@Inject
	protected IProxyTestStrategy testStrategy;

	private boolean stopAppsOnShutdown;

	private static final String PROPERTY_STOP_PROXIES_ON_SHUTDOWN = "proxy.stop-proxies-on-shutdown";

	@PostConstruct
	public void init() {
	    stopAppsOnShutdown = Boolean.parseBoolean(environment.getProperty(PROPERTY_STOP_PROXIES_ON_SHUTDOWN, "true"));
	}

	@PreDestroy
	public void shutdown() {
		if (!stopAppsOnShutdown) {
			return;
		}
		try {
			containerKiller.shutdown();
		} finally {
			for (Proxy proxy : activeProxies.getAllProxies()) {
				try {
					backend.stopProxy(proxy);
				} catch (Exception exception) {
					exception.printStackTrace();
				}
			}
		}
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
	 * Find a proxy using its ID.
	 * Without authentication check.
	 * 
	 * @param id The ID of the proxy to find.
	 * @return The matching proxy, or null if no match was found.
	 */
	public Proxy getProxy(String id) {
		return activeProxies.getProxy(id);
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
		// TODO remove filter option
		boolean isAdmin = userService.isAdmin();
		List<Proxy> matches = new ArrayList<>();
		for (Proxy proxy: activeProxies.getAllProxies()) {
			boolean hasAccess = ignoreAccessControl || isAdmin || userService.isOwner(proxy);
			if (hasAccess && (filter == null || filter.test(proxy))) matches.add(proxy);
		}
		return matches;
	}

	/**
	 * Find all proxies that match an optional filter and that are owned by the current user.
	 *
	 * @param filter The filter to match, or null.
	 * @return A List of matching proxies, may be empty.
	 */
	public List<Proxy> getProxiesOfCurrentUser(Predicate<Proxy> filter) {
		List<Proxy> matches = new ArrayList<>();
		for (Proxy proxy: activeProxies.getAllProxies()) {
			boolean hasAccess = userService.isOwner(proxy);
			if (hasAccess && (filter == null || filter.test(proxy))) matches.add(proxy);
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
	public Proxy startProxy(ProxySpec spec, boolean ignoreAccessControl) throws ContainerProxyException, InvalidParametersException {
	    return startProxy(spec, ignoreAccessControl, null, UUID.randomUUID().toString(), null);
    }

	/**
	 * Launch a new proxy using the given ProxySpec.
	 * 
	 * @param spec The ProxySpec to base the new proxy on.
	 * @param ignoreAccessControl True to allow access to the given ProxySpec, regardless of the current security context.
     * @param runtimeValues Initial RuntimeValues of the Proxy.
	 * @return The newly launched proxy.
	 * @throws ContainerProxyException If the proxy fails to start for any reason.
	 */
	public Proxy startProxy(ProxySpec spec, boolean ignoreAccessControl, List<RuntimeValue> runtimeValues, String proxyId, Map<String, String> parameters) throws ContainerProxyException, InvalidParametersException {
		if (!ignoreAccessControl && !userService.canAccess(spec)) {
			throw new AccessDeniedException(String.format("Cannot start proxy %s: access denied", spec.getId()));
		}

		Proxy.ProxyBuilder proxyBuilder = Proxy.builder();
		proxyBuilder.id(proxyId);
		proxyBuilder.status(ProxyStatus.New);
		proxyBuilder.userId(userService.getCurrentUserId());
		proxyBuilder.specId(spec.getId());
		proxyBuilder.createdTimestamp(System.currentTimeMillis());

		if (spec.getDisplayName() != null) {
			proxyBuilder.displayName(spec.getDisplayName());
		} else {
			proxyBuilder.displayName(spec.getId());
		}

		if (runtimeValues != null) {
			proxyBuilder.addRuntimeValues(runtimeValues);
		}

		Proxy currentProxy = proxyBuilder.build();
		saveProxy(currentProxy);
		try {

			currentProxy = runtimeValueService.addRuntimeValuesBeforeSpel(spec, parameters, currentProxy);
			currentProxy = backend.addRuntimeValuesBeforeSpel(spec, currentProxy);

			SpecExpressionContext context = SpecExpressionContext.create(
					currentProxy,
					spec,
					userService.getCurrentAuth().getPrincipal(),
					userService.getCurrentAuth().getCredentials());

			// resolve SpEL expression in spec
			spec = spec.resolve(expressionResolver, context);

			// add the runtime values which depend on spel to be resolved (and thus cannot be used in spel expression)
			currentProxy = runtimeValueService.addRuntimeValuesAfterSpel(spec, currentProxy);

			currentProxy = currentProxy.toBuilder().status(ProxyStatus.Starting).build(); // TODO update proxy
		} catch (Throwable t) {
			removeProxy(currentProxy);
			applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(this, currentProxy.getUserId(), spec.getId()));
			throw new ContainerProxyException("Container failed to start", t);
		}

		try {
			currentProxy = backend.startProxy(currentProxy, spec);
		} catch (ProxyFailedToStartException t) {
			try {
				backend.stopProxy(t.getProxy());
			} catch (Throwable t2) {
				// log error, but ignore it otherwise
				// most important is that we remove the proxy from memory
				log.warn("Error while stopping failed proxy", t2);
			}
			removeProxy(t.getProxy());
			applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(this, t.getProxy().getUserId(), spec.getId()));
			throw new ContainerProxyException("Container failed to start", t);
		} catch (Throwable t) {
			removeProxy(currentProxy);
			applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(this, currentProxy.getUserId(), spec.getId()));
			throw new ContainerProxyException("Container failed to start", t);
		}

	// TODO
//		if (proxyBuilder.getStatus().equals(ProxyStatus.Stopped) || proxyBuilder.getStatus().equals(ProxyStatus.Stopping)) {
//			log.info(String.format("Pending proxy cleaned up [user: %s] [spec: %s] [id: %s]", proxyBuilder.getUserId(), proxyBuilder.getSpecId(), proxyBuilder.getId()));
//			return proxyBuilder;
//		}

		if (!testStrategy.testProxy(currentProxy)) {
			backend.stopProxy(currentProxy);
			proxyStatusService.applicationStartupFailed(currentProxy.getId(), currentProxy.getContainers().size());
			throw new ContainerProxyException("Container did not respond in time");
		}

		currentProxy = currentProxy.toBuilder()
				.startupTimestamp(System.currentTimeMillis())
				.status(ProxyStatus.Up)
				.build();

		proxyStatusService.proxyStarted(currentProxy.getId(), currentProxy.getContainers().size());
		setupProxy(currentProxy);

		log.info(String.format("Proxy activated [user: %s] [spec: %s] [id: %s]", currentProxy.getUserId(), spec.getId(), currentProxy.getContainers().get(0).getId()));
		applicationEventPublisher.publishEvent(new ProxyStartEvent(this, currentProxy.getId(), currentProxy.getUserId(), spec.getId()));

		activeProxies.update(currentProxy);

		return currentProxy;
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

		Runnable releaser = () -> {
			try {
				backend.stopProxy(proxy);
				logService.detach(proxy);
				log.info(String.format("Proxy released [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpecId(), proxy.getId()));
				if (proxy.getStartupTimestamp() == 0) {
					applicationEventPublisher.publishEvent(new ProxyStopEvent(this, proxy.getUserId(), proxy.getSpecId(), null));
				} else {
					applicationEventPublisher.publishEvent(new ProxyStopEvent(this, proxy.getUserId(), proxy.getSpecId(),
							Duration.ofMillis(System.currentTimeMillis() - proxy.getStartupTimestamp())));
				}
				removeProxy(proxy);
			} catch (Exception e){
				log.error("Failed to release proxy " + proxy.getId(), e);
			}
		};
		if (async) containerKiller.submit(releaser);
		else releaser.run();

		for (Container container : proxy.getContainers()) {
			for (Entry<String, URI> target : container.getTargets().entrySet()) {
				mappingManager.removeMapping(target.getKey());
			}
		}
	}

	public void pauseProxy(Proxy proxy, boolean async, boolean ignoreAccessControl) {
		if (!ignoreAccessControl && !userService.isAdmin() && !userService.isOwner(proxy)) {
			throw new AccessDeniedException(String.format("Cannot pause proxy %s: access denied", proxy.getId()));
		}

		if (!backend.supportsPause()) {
			throw new IllegalArgumentException("Trying to pause a proxy when the backend does not support pausing apps");
		}

		Runnable releaser = () -> {
			try {
				backend.pauseProxy(proxy);
				logService.detach(proxy);
				log.info(String.format("Proxy paused [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpecId(), proxy.getId()));
				// TODO
//				if (proxy.getStartupTimestamp() == 0) {
//					applicationEventPublisher.publishEvent(new ProxyStopEvent(this, proxy.getUserId(), proxy.getSpec().getId(), null));
//				} else {
//					applicationEventPublisher.publishEvent(new ProxyStopEvent(this, proxy.getUserId(), proxy.getSpec().getId(),
//							Duration.ofMillis(System.currentTimeMillis() - proxy.getStartupTimestamp())));
//				}
			} catch (Exception e){
				log.error("Failed to pause proxy " + proxy.getId(), e);
			}
		};
		if (async) containerKiller.submit(releaser);
		else releaser.run();

		for (Container container : proxy.getContainers()) {
			for (Entry<String, URI> target : container.getTargets().entrySet()) {
				mappingManager.removeMapping(target.getKey());
			}
		}
	}

	public void unPauseProxy(Proxy proxy, boolean async, boolean ignoreAccessControl) {
//		if (!ignoreAccessControl && !userService.isAdmin() && !userService.isOwner(proxy)) {
//			throw new AccessDeniedException(String.format("Cannot pause proxy %s: access denied", proxy.getId()));
//		}
//
//		if (!backend.supportsPause()) {
//			throw new IllegalArgumentException("Trying to pause a proxy when the backend does not support pausing apps");
//		}
//
//		Runnable releaser = () -> {
//			try {
//				// When resuming the proxy, we *do* want to re-evaluate the environment variables
//				// therefore we fetch the latest version of the spec and evaluate SpeL
//				ProxySpec spec = baseSpecProvider.getSpec(proxy.getSpecId());
//
//				spec = prepareProxyForStart(initialProxy, proxy, spec, null); // TODO support parameters
//
//				backend.resumeProxy(proxy, spec); // TODO handle errors
//
//				// TODO handle stopped pending app
//
//				if (!testStrategy.testProxy(proxy)) {
//					backend.stopProxy(proxy);
//					proxyStatusService.applicationStartupFailed(proxy);
//					throw new ContainerProxyException("Container did not respond in time");
//				}
//
//				proxy.setStatus(ProxyStatus.Up);
//
//				log.info(String.format("Proxy resumed [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpecId(), proxy.getId()));
//
//				// TODO
////				if (proxy.getStartupTimestamp() == 0) {
////					applicationEventPublisher.publishEvent(new ProxyStopEvent(this, proxy.getUserId(), proxy.getSpec().getId(), null));
////				} else {
////					applicationEventPublisher.publishEvent(new ProxyStopEvent(this, proxy.getUserId(), proxy.getSpec().getId(),
////							Duration.ofMillis(System.currentTimeMillis() - proxy.getStartupTimestamp())));
////				}
//
//				setupProxy(proxy);
//			} catch (Exception e){
//				log.error("Failed to resume proxy " + proxy.getId(), e);
//			}
//		};
//		if (async) containerKiller.submit(releaser);
//		else releaser.run();

	}


	/**
	 * Add existing Proxy to the ProxyService.
	 * This is used by the AppRecovery feature.
	 * @param proxy
	 */
	public void addExistingProxy(Proxy proxy) {
		activeProxies.addProxy(proxy);

		setupProxy(proxy);

		log.info(String.format("Existing Proxy re-activated [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpecId(), proxy.getId()));
	}

	/**
	 * Setups the Mapping of and logging of the proxy.
	 */
	private void setupProxy(Proxy proxy) {
		for (Container container : proxy.getContainers()) {
			for (Entry<String, URI> target : container.getTargets().entrySet()) {
				mappingManager.addMapping(proxy.getId(), target.getKey(), target.getValue());
			}
		}

		if (logService.isLoggingEnabled()) {
			BiConsumer<OutputStream, OutputStream> outputAttacher = backend.getOutputAttacher(proxy);
			if (outputAttacher == null) {
				log.warn("Cannot log proxy output: " + backend.getClass() + " does not support output attaching.");
			} else {
				logService.attachToOutput(proxy, outputAttacher);
			}
		}
	}

	private void saveProxy(Proxy proxy) {
		activeProxies.addProxy(proxy);
		proxyStatusService.proxyCreated(proxy.getId());
	}

	private void removeProxy(Proxy proxy) {
		activeProxies.removeProxy(proxy);
		proxyStatusService.proxyRemoved(proxy.getId());
	}

}
