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
import eu.openanalytics.containerproxy.backend.dispatcher.ProxyDispatcherService;
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
import java.util.stream.Stream;

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

    public static final String PROPERTY_STOP_PROXIES_ON_SHUTDOWN = "proxy.stop-proxies-on-shutdown";
    private final StructuredLogger log = StructuredLogger.create(getClass());
    private final Set<String> actionsInProgress = new HashSet<>();
    @Inject
    protected IProxyTestStrategy testStrategy;
    @Inject
    private IProxyStore proxyStore;
    @Inject
    private IProxySpecProvider baseSpecProvider;
    @Inject
    private ProxyDispatcherService proxyDispatcherService;
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
    private boolean stopAppsOnShutdown;
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
                proxyDispatcherService.getDispatcher(proxy.getSpecId()).stopProxy(proxy);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }


    /**
     * Find the ProxySpec that matches the given ID and check access control.
     *
     * @param id    The ID to look for.
     * @return A matching ProxySpec, or null if no match was found.
     */
    public ProxySpec getUserSpec(String id) {
        if (id == null || id.isEmpty()) return null;
        ProxySpec proxySpec = baseSpecProvider.getSpec(id);
        if (userService.canAccess(proxySpec)) {
            return proxySpec;
        }
        return null;
    }

    /**
     * Find all ProxySpecs that can be accessed by the current user.
     *
     * @return A List of matching ProxySpecs, may be empty.
     */
    public List<ProxySpec> getUserSpecs() {
        return baseSpecProvider.getSpecs().stream()
            .filter(spec -> userService.canAccess(spec))
            .toList();
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
     * Get the proxies of the given specId that are owned by the current user.
     *
     * @return A List of matching proxies, may be empty.
     */
    public Stream<Proxy> getUserProxiesBySpecId(String specId) {
        return proxyStore.getAllProxies().stream().filter(p -> userService.isOwner(p) && p.getSpecId().equals(specId));
    }

    /**
     * Get all proxies that are owned by the current user.
     *
     * @return A List of matching proxies, may be empty.
     */
    public List<Proxy> getUserProxies() {
        return proxyStore.getAllProxies().stream().filter(p -> userService.isOwner(p)).toList();
    }

    /**
     * Get all proxies that are owned by the given user.
     *
     * @return A List of matching proxies, may be empty.
     */
    public List<Proxy> getUserProxies(String userId) {
        return proxyStore.getAllProxies().stream().filter(p -> p.getUserId().equals(userId)).toList();
    }

    /**
     * Get all proxies.
     *
     * @return A List of all proxies.
     */
    public List<Proxy> getAllProxies() {
        return new ArrayList<>(proxyStore.getAllProxies());
    }

    /**
     * Get all Up proxies.
     *
     * @return A List of all Up proxies.
     */
    public List<Proxy> getAllUpProxies() {
        return proxyStore.getAllProxies().stream().filter(p -> p.getStatus().equals(ProxyStatus.Up)).toList();
    }

    /**
     * Launch a new proxy using the given ProxySpec.
     *
     * @param spec The ProxySpec to base the new proxy on.
     * @return The newly launched proxy.
     */
    public Proxy startProxy(ProxySpec spec) {
        String id = UUID.randomUUID().toString();
        startProxy(userService.getCurrentAuth(), spec, null, id, null).run();
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
            proxyBuilder.targetId(proxyId);
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
                log.info(proxy, "Proxy being stopped by user " + UserService.getUserId(user));
            }

            Proxy stoppingProxy = proxy.withStatus(ProxyStatus.Stopping);
            proxyStore.updateProxy(stoppingProxy);

            for (Entry<String, URI> target : proxy.getTargets().entrySet()) {
                mappingManager.removeMapping(proxy, target.getKey());
            }
            return stoppingProxy;
        }, (stoppingProxy) -> {
            Proxy stoppedProxy = stoppingProxy.withStatus(ProxyStatus.Stopped);
            try {
                proxyDispatcherService.getDispatcher(proxy.getSpecId()).stopProxy(stoppedProxy);
            } catch (Throwable t) {
                log.error(stoppedProxy, t, "Failed to remove proxy");
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

            if (!proxyDispatcherService.getDispatcher(proxy.getSpecId()).supportsPause()) {
                log.warn(proxy, "Trying to pause a proxy when the backend does not support pausing apps");
                throw new IllegalArgumentException("Trying to pause a proxy when the backend does not support pausing apps");
            }

            if (user != null && !ignoreAccessControl) {
                log.info(proxy, "Proxy being paused by user " + UserService.getUserId(user));
            }

            Proxy stoppingProxy = proxy.withStatus(ProxyStatus.Pausing);
            proxyStore.updateProxy(stoppingProxy);

            for (Entry<String, URI> target : proxy.getTargets().entrySet()) {
                mappingManager.removeMapping(proxy, target.getKey());
            }
            return stoppingProxy;

        }, (pausingProxy) -> {
            try {
                proxyDispatcherService.getDispatcher(pausingProxy.getSpecId()).pauseProxy(pausingProxy);
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

            if (!proxyDispatcherService.getDispatcher(proxy.getSpecId()).supportsPause()) {
                log.warn(proxy, "Trying to resume a proxy when the backend does not support pausing apps");
                throw new IllegalArgumentException("Trying to resume a proxy when the backend does not support pausing apps");
            }

            Proxy resumingProxy = proxy.withStatus(ProxyStatus.Resuming);
            Proxy parameterizedProxy = runtimeValueService.processParameters(user, getUserSpec(proxy.getSpecId()), parameters, resumingProxy);
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
            proxy = proxyDispatcherService.getDispatcher(spec.getId()).addRuntimeValuesBeforeSpel(user, spec, proxy);

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
            for (ContainerSpec containerSpec : spec.getContainerSpecs()) {
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
            log.warn(proxy, t, "Failed to prepare proxy for start");
            throw new ProxyFailedToStartException("Container failed to start", t, proxy);
        }
    }

    private Proxy startOrResumeProxy(Authentication user, Proxy proxy, ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLog) {
        ProxySpec spec = baseSpecProvider.getSpec(proxy.getSpecId());

        try {
            Pair<ProxySpec, Proxy> r = prepareProxyForStart(user, proxy, spec);
            spec = r.getFirst();
            proxy = r.getSecond();
            if (proxy.getStatus() == ProxyStatus.New) {
                log.info(proxy, "Starting proxy");
                proxy = proxyDispatcherService.getDispatcher(spec.getId()).startProxy(user, proxy, spec, proxyStartupLog);
            } else if (proxy.getStatus() == ProxyStatus.Resuming) {
                log.info(proxy, "Resuming proxy");
                proxy = proxyDispatcherService.getDispatcher(spec.getId()).resumeProxy(user, proxy, spec);
            } else {
                throw new ContainerProxyException("Cannot start or resume proxy because status is invalid");
            }
        } catch (ProxyFailedToStartException t) {
            log.warn(t.getProxy(), t, "Proxy failed to start");
            try {
                proxyDispatcherService.getDispatcher(spec.getId()).stopProxy(t.getProxy());
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
                proxyDispatcherService.getDispatcher(spec.getId()).stopProxy(proxy);
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

//        if (proxyStartupLog != null) { // TODO
//            proxyStartupLog.applicationStarted();
//        }

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
            proxyDispatcherService.getDispatcher(startingProxy.getSpecId()).stopProxy(startingProxy);
            log.info(startingProxy, "Pending proxy cleaned up");
            return true;
        }
        return false;
    }

    /**
     * Add existing Proxy to the ProxyService.
     * This is used by the AppRecovery feature.
     *
     * @param proxy
     */
    public void addExistingProxy(Proxy proxy) {
        proxyStore.addProxy(proxy);

        setupProxy(proxy);

        log.info(proxy, "Existing Proxy re-activated");
    }

    /**
     * Setups the Mapping of proxy.
     */
    private void setupProxy(Proxy proxy) {
        for (Entry<String, URI> target : proxy.getTargets().entrySet()) {
            mappingManager.addMapping(proxy, target.getKey(), target.getValue());
        }
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
     *
     * @param proxyId the proxyId for which the action is performed.
     */
    private synchronized void actionStarted(String proxyId) {
        actionsInProgress.add(proxyId);
    }

    /**
     * Called after a (long during) proxy action has finished.
     * Required in order to check whether such an action is in progress.
     *
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
        } catch (Throwable t) {
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
