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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.event.ProxyPauseEvent;
import eu.openanalytics.containerproxy.event.ProxyResumeEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.data.util.Pair;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
		
	private final StructuredLogger log = StructuredLogger.create(getClass());

	@Inject
	private IProxyStore proxyStore;

	@Inject
	private IProxySpecProvider baseSpecProvider;
	
	@Inject
	private IContainerBackend backend;

	@Inject
	private ProxyMappingManager mappingManager;
	
	@Inject
	private UserService userService;
	
	@Inject
	private ApplicationEventPublisher applicationEventPublisher;

	@Inject
	private Environment environment;

	@Inject
	private RuntimeValueService runtimeValueService;

	@Inject
	private SpecExpressionResolver expressionResolver;

	@Inject
	protected IProxyTestStrategy testStrategy;

	private boolean stopAppsOnShutdown;

	public static final String PROPERTY_STOP_PROXIES_ON_SHUTDOWN = "proxy.stop-proxies-on-shutdown";

	private final Set<String> actionsInProgress = new HashSet<>();
	private Pair<String, Instant> lastStop = null;

	@PostConstruct
	public void init() {
	    stopAppsOnShutdown = Boolean.parseBoolean(environment.getProperty(PROPERTY_STOP_PROXIES_ON_SHUTDOWN, "true"));
	}

	@PreDestroy
	public void shutdown() {
		if (!stopAppsOnShutdown) {
			return;
		}
		for (Proxy proxy : proxyStore.getAllProxies()) {
			try {
				backend.stopProxy(proxy);
			} catch (Exception exception) {
				exception.printStackTrace();
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
		return proxyStore.getProxy(id);
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
		for (Proxy proxy: proxyStore.getAllProxies()) {
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
		for (Proxy proxy: proxyStore.getAllProxies()) {
			boolean hasAccess = userService.isOwner(proxy);
			if (hasAccess && (filter == null || filter.test(proxy))) matches.add(proxy);
		}
		return matches;
	}

	/**
	 * Launch a new proxy using the given ProxySpec.
	 *
	 * @param spec The ProxySpec to base the new proxy on.
	 * @return The newly launched proxy.
	 */
	public Proxy startProxy(ProxySpec spec) {
		String id = UUID.randomUUID().toString();
	    startProxy(userService.getCurrentAuth(), spec,  null, id, null).run();
		return getProxy(id);
    }

	/**
	 * Launch a new proxy using the given ProxySpec.
	 *
	 * @param spec The ProxySpec to base the new proxy on.
	 * @return The newly launched proxy.
	 */
	public Command startProxy(Authentication user, ProxySpec spec, List<RuntimeValue> runtimeValues, String proxyId, Map<String, String> parameters) {
		return action(proxyId, () -> {
			if (!userService.canAccess(user, spec)) {
				throw new AccessDeniedException(String.format("Cannot start proxy %s: access denied", spec.getId()));
			}
			Proxy.ProxyBuilder proxyBuilder = Proxy.builder();
			proxyBuilder.id(proxyId);
			proxyBuilder.status(ProxyStatus.New);
			proxyBuilder.userId(UserService.getUserId(user));
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

			Proxy currentProxy = runtimeValueService.processParameters(user, spec, parameters, proxyBuilder.build());
			proxyStore.addProxy(currentProxy);
			return currentProxy;
		}, (currentProxy) -> {
			ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLog = new ProxyStartupLog.ProxyStartupLogBuilder();
			Proxy result = startOrResumeProxy(user, currentProxy, proxyStartupLog);

			if (result != null) {
				log.info(result, "Proxy activated");
				applicationEventPublisher.publishEvent(new ProxyStartEvent(result, proxyStartupLog.succeeded()));

				// final check to see if the app was stopped
				cleanupIfPendingAppWasStopped(result);
			}
		});
	}

	/**
	 * Stop a running proxy.
	 *
	 * @param user
	 * @param proxy               The proxy to stop.
	 * @param ignoreAccessControl True to allow access to any proxy, regardless of the current security context.
	 */
	public Command stopProxy(Authentication user, Proxy proxy, boolean ignoreAccessControl) {
		return action(proxy.getId(), () -> {
			if (!ignoreAccessControl && !userService.isAdmin(user) && !userService.isOwner(user, proxy)) {
				throw new AccessDeniedException(String.format("Cannot stop proxy %s: access denied", proxy.getId()));
			}

			if (user != null && !ignoreAccessControl) {
				log.info(proxy, "Proxy being stopped by user " +  UserService.getUserId(user));
			}

			Proxy stoppingProxy = proxy.withStatus(ProxyStatus.Stopping);
			proxyStore.updateProxy(stoppingProxy);

			for (Entry<String, URI> target : proxy.getTargets().entrySet()) {
				mappingManager.removeMapping(target.getKey());
			}
			return stoppingProxy;
		}, (stoppingProxy) -> {
			Proxy stoppedProxy = stoppingProxy.withStatus(ProxyStatus.Stopped);
			try {
				backend.stopProxy(stoppedProxy);
			} catch (Throwable t) {
				log.error( stoppedProxy, t, "Failed to remove proxy");
			}
			try {
				proxyStore.removeProxy(stoppedProxy);
			} catch (Throwable t) {
				log.error(stoppedProxy, t, "Failed to remove proxy");
			}
			log.info(stoppedProxy, "Proxy released");

			applicationEventPublisher.publishEvent(new ProxyStopEvent(stoppedProxy));
		});
	}

	public Command pauseProxy(Authentication user, Proxy proxy, boolean ignoreAccessControl) {
		return action(proxy.getId(), () -> {
			if (!ignoreAccessControl && !userService.isAdmin(user) && !userService.isOwner(user, proxy)) {
				throw new AccessDeniedException(String.format("Cannot pause proxy %s: access denied", proxy.getId()));
			}

			if (!backend.supportsPause()) {
				log.warn(proxy, "Trying to pause a proxy when the backend does not support pausing apps");
				throw new IllegalArgumentException("Trying to pause a proxy when the backend does not support pausing apps");
			}

			if (user != null && !ignoreAccessControl) {
				log.info(proxy, "Proxy being paused by user " +  UserService.getUserId(user));
			}

			Proxy stoppingProxy = proxy.withStatus(ProxyStatus.Pausing);
			proxyStore.updateProxy(stoppingProxy);

			for (Entry<String, URI> target : proxy.getTargets().entrySet()) {
				mappingManager.removeMapping(target.getKey());
			}
			return stoppingProxy;

		}, (pausingProxy) -> {
			try {
				backend.pauseProxy(pausingProxy);
				Proxy pausedProxy = pausingProxy.withStatus(ProxyStatus.Paused);
				proxyStore.updateProxy(pausedProxy);
				log.info(pausedProxy, "Proxy paused");
				applicationEventPublisher.publishEvent(new ProxyPauseEvent(pausedProxy));
			} catch (Throwable t) {
				log.error(pausingProxy, t, "Failed to pause proxy ");
			}
		});
	}

	public Command resumeProxy(Authentication user, Proxy proxy, Map<String, String> parameters) {
		return action(proxy.getId(), () -> {
			if (!userService.isOwner(user, proxy)) {
				throw new AccessDeniedException(String.format("Cannot resume proxy %s: access denied", proxy.getId()));
			}

			if (!backend.supportsPause()) {
				log.warn(proxy, "Trying to resume a proxy when the backend does not support pausing apps");
				throw new IllegalArgumentException("Trying to resume a proxy when the backend does not support pausing apps");
			}

			Proxy resumingProxy = proxy.withStatus(ProxyStatus.Resuming);
			Proxy parameterizedProxy = runtimeValueService.processParameters(user, getProxySpec(proxy.getSpecId()), parameters, resumingProxy);
			proxyStore.updateProxy(parameterizedProxy);
			return parameterizedProxy;
		}, (parameterizedProxy) -> {
			// TODO proxystartuplog?
			Proxy result = startOrResumeProxy(user, parameterizedProxy, null);

			if (result != null) {
				log.info(result, "Proxy resumed");
				applicationEventPublisher.publishEvent(new ProxyResumeEvent(result));

				// final check to see if the app was stopped
				cleanupIfPendingAppWasStopped(result);
			}
		});
	}

	private Pair<ProxySpec, Proxy> prepareProxyForStart(Authentication user, Proxy proxy, ProxySpec spec) {
		try {
			proxy = runtimeValueService.addRuntimeValuesBeforeSpel(user, spec, proxy);
			proxy = backend.addRuntimeValuesBeforeSpel(user, spec, proxy);

			SpecExpressionContext context = SpecExpressionContext.create(
					proxy,
					spec,
					user,
					user.getPrincipal(),
					user.getCredentials());

			// resolve SpEL expression in spec
			spec = spec.firstResolve(expressionResolver, context);

			// add the runtime values which depend on spel to be resolved (and thus cannot be used in spel expression)
			proxy = runtimeValueService.addRuntimeValuesAfterSpel(spec, proxy);

			// create container objects
			for (ContainerSpec containerSpec: spec.getContainerSpecs()) {
				Container.ContainerBuilder containerBuilder = Container.builder();
				containerBuilder.index(containerSpec.getIndex());
				Container container = containerBuilder.build();

				container = runtimeValueService.addRuntimeValuesAfterSpel(containerSpec, container);
				proxy = proxy.toBuilder().addContainer(container).build();
			}

			context = context.copy(spec, proxy);

			spec = spec.finalResolve(expressionResolver, context);

			return Pair.of(spec, proxy);
		} catch (Throwable t) {
			try {
				backend.stopProxy(proxy); // stop in case we are resuming
			} catch (Throwable t2) {
				// log error, but ignore it otherwise
				// most important is that we remove the proxy from memory
				log.error(proxy, t, "Error while stopping failed proxy");
			}
			proxyStore.removeProxy(proxy);
			applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(proxy));
			log.warn( proxy, t, "Failed to prepare proxy for start");
			throw new ContainerProxyException("Container failed to start", t);
		}
	}

	private Proxy startOrResumeProxy(Authentication user, Proxy proxy, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLog) {
		ProxySpec spec = baseSpecProvider.getSpec(proxy.getSpecId());
		Pair<ProxySpec, Proxy> r = prepareProxyForStart(user, proxy, spec);
		spec = r.getFirst();
		proxy = r.getSecond();

		try {
			if (proxy.getStatus() == ProxyStatus.New) {
				log.info(proxy, "Starting proxy");
				proxy = backend.startProxy(user, proxy, spec, proxyStartupLog);
			} else if (proxy.getStatus() == ProxyStatus.Resuming) {
				log.info(proxy, "Resuming proxy");
				proxy = backend.resumeProxy(user, proxy, spec);
			} else {
				throw new ContainerProxyException("Cannot start or resume proxy because status is invalid");
			}
		} catch (ProxyFailedToStartException t) {
			log.warn(t.getProxy(), t, "Proxy failed to start");
			try {
				backend.stopProxy(t.getProxy());
			} catch (Throwable t2) {
				// log error, but ignore it otherwise
				// most important is that we remove the proxy from memory
				log.warn(t.getProxy(), t, "Error while stopping failed proxy");
			}
			proxyStore.removeProxy(t.getProxy());
			applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(t.getProxy()));
			throw new ContainerProxyException("Container failed to start", t);
		} catch (Throwable t) {
			log.warn(proxy, t, "Proxy failed to start");
			proxyStore.removeProxy(proxy);
			applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(proxy));
			throw new ContainerProxyException("Container failed to start", t);
		}

		if (cleanupIfPendingAppWasStopped(proxy)) {
			return null;
		}

		if (!testStrategy.testProxy(proxy)) {
			try {
				backend.stopProxy(proxy);
			} catch (Throwable t) {
				// log error, but ignore it otherwise
				// most important is that we remove the proxy from memory
				log.warn(proxy, t, "Error while stopping failed proxy");
			}
			proxyStore.removeProxy(proxy);
			applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(proxy));
			log.warn(proxy, "Container did not respond in time");
			throw new ContainerProxyException("Container did not respond in time");
		}

		if (proxyStartupLog != null) {
			proxyStartupLog.applicationStarted();
		}

		if (cleanupIfPendingAppWasStopped(proxy)) {
			return null;
		}

		proxy = proxy.toBuilder()
				.startupTimestamp(System.currentTimeMillis())
				.status(ProxyStatus.Up)
				.build();

		setupProxy(proxy);

		proxyStore.updateProxy(proxy);

		return proxy;
	}

	private boolean cleanupIfPendingAppWasStopped(Proxy startingProxy) {
		// fetch proxy from proxyStore in order to check if it was stopped
		Proxy proxy = getProxy(startingProxy.getId());
		if (proxy == null || proxy.getStatus().equals(ProxyStatus.Stopped)
				|| proxy.getStatus().equals(ProxyStatus.Stopping)) {
			backend.stopProxy(startingProxy);
			log.info(startingProxy, "Pending proxy cleaned up");
			return true;
		}
		return false;
	}

	/**
	 * Add existing Proxy to the ProxyService.
	 * This is used by the AppRecovery feature.
	 * @param proxy
	 */
	public void addExistingProxy(Proxy proxy) {
		proxyStore.addProxy(proxy);

		setupProxy(proxy);

		log.info(proxy, "Existing Proxy re-activated");
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
	}

	private String formatLogMessage(String message, Proxy proxy) {
		if (proxy == null) {
			return String.format("%s [unknown proxy]", message);
		}
		return String.format("%s [user: %s] [spec: %s] [id: %s]", message, proxy.getUserId(), proxy.getSpecId(), proxy.getId());
	}

	/**
	 * @return whether any long during proxy actions are being executed.
	 */
	public synchronized boolean isBusy() {
		if (!actionsInProgress.isEmpty()) {
			// action in progress -> service is busy
			return true;
		}
		// if last stop was less than 1 minute ago -> service is busy
		return lastStop != null && Duration.between(lastStop.getSecond(), Instant.now()).toMinutes() <= 1;
	}

	/**
	 * Called before a (long during) proxy action is executed.
	 * Required in order to check whether such an action is in progress.
	 * @param proxyId the proxyId for which the action is performed.
	 */
	private synchronized void actionStarted(String proxyId) {
		actionsInProgress.add(proxyId);
	}

	/**
	 * Called after a (long during) proxy action has finished.
	 * Required in order to check whether such an action is in progress.
	 * @param proxyId the proxyId for which the action has been performed.
	 */
	private synchronized void actionFinished(String proxyId) {
		actionsInProgress.remove(proxyId);
		lastStop = Pair.of(proxyId, Instant.now());
	}

	private Command action(String proxyId, BlockingAction blocking, AsyncAction async) {
		try {
			actionStarted(proxyId);
			Proxy proxy = blocking.run();
			return () -> {
				try {
					async.run(proxy);
				} finally {
					actionFinished(proxyId);
				}
			};
		} catch(Throwable t) {
			actionFinished(proxyId);
			throw t;
		}
	}

	public interface Command extends Runnable {
	}

	@FunctionalInterface
	private interface AsyncAction {
		void run(Proxy proxy);
	}

	@FunctionalInterface
	private interface BlockingAction {
		Proxy run();
	}

}
