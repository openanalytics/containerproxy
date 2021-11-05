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

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CreatedTimestampKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxyIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxySpecIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.expression.ExpressionAwareContainerSpec;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to recover running apps after restart.
 * This service does not contain code for specific backends, instead it delegates these parts to the
 * scanExistingContainers and setupPortMappingExistingProxy methods of the container backends.
 */
@Service
public class AppRecoveryService {

	protected static final String PROPERTY_RECOVER_RUNNING_PROXIES = "proxy.recover-running-proxies";
	protected static final String PROPERTY_RECOVER_RUNNING_PROXIES_FROM_DIFFERENT_CONFIG = "proxy.recover-running-proxies-from-different-config";

	private final Logger log = LogManager.getLogger(AppRecoveryService.class);

	@Inject
	private Environment environment;

	@Inject
	private IContainerBackend containerBackend;

	@Inject
	private IProxySpecProvider proxySpecProvider;

	@Inject
	private ProxyService proxyService;

	@Inject
	private HeartbeatService heartbeatService;

	@Inject
	private SpecExpressionResolver expressionResolver;

	@Inject
	private IdentifierService identifierService;

	private boolean isReady = false;

	private boolean recoverFromDifferentConfig;

	@EventListener(ApplicationReadyEvent.class)
	public void recoverRunningApps() throws Exception {
		if (Boolean.parseBoolean(environment.getProperty(PROPERTY_RECOVER_RUNNING_PROXIES, "false"))) {
			recoverFromDifferentConfig = Boolean.parseBoolean(environment.getProperty(PROPERTY_RECOVER_RUNNING_PROXIES_FROM_DIFFERENT_CONFIG, "false"));

			if (recoverFromDifferentConfig)  {
				log.info("Recovery of running apps enabled (even apps started with a different config file)");
			} else {
				log.info("Recovery of running apps enabled (but only apps started with the current config file)");
			}

			Map<String, Proxy> proxies = new HashMap<>();

			for (ExistingContainerInfo containerInfo: containerBackend.scanExistingContainers()) {
			    String proxyId = containerInfo.getRuntimeValue(ProxyIdKey.inst).getValue();

				if (!proxies.containsKey(proxyId)) {
					ProxySpec proxySpec = proxySpecProvider.getSpec(containerInfo.getRuntimeValue(ProxySpecIdKey.inst).getValue());
					if (proxySpec == null) {
						log.warn(String.format("Found existing container (%s) but not corresponding proxy spec.", containerInfo.getContainerId()));
						continue;
					}
					Proxy proxy = new Proxy();
					proxy.setId(proxyId);
					proxy.setSpec(proxySpec);
					proxy.setStatus(ProxyStatus.Stopped);
					proxy.setCreatedTimestamp(Long.parseLong(containerInfo.getRuntimeValue(CreatedTimestampKey.inst).getValue()));
					// we cannot store the startUpTimestamp in the ContainerBackend, therefore when recovering apps
					// we set the startUpTimestamp to the time the proxy was created. The distinction between created
					// and started is only important for the events (e.g. Prometheus) not for the whole application.
					proxy.setStartupTimestamp(proxy.getCreatedTimestamp());
					proxy.setUserId(containerInfo.getRuntimeValue(UserIdKey.inst).getValue());
					proxy.addRuntimeValues(containerInfo.getRuntimeValues());

					proxies.put(proxyId, proxy);
				}
				Proxy proxy = proxies.get(proxyId);
				Container container = new Container();
				container.setId(containerInfo.getContainerId());
				container.setParameters(containerInfo.getParameters());
				ContainerSpec containerSpec = proxy.getSpec().getContainerSpec(containerInfo.getImage());
				if (containerSpec == null) {
					log.warn(String.format("Found existing container (%s) but not corresponding container spec.", containerInfo.getContainerId()));
					continue;
				}
				container.setSpec(containerSpec);
				proxy.addContainer(container);
				proxy.setStatus(ProxyStatus.Up);

				setupPortMapping(proxy, container, containerInfo);

				proxySpecProvider.postProcessRecoveredProxy(proxy);
			}

			for (Proxy proxy: proxies.values()) {
				proxyService.addExistingProxy(proxy);
				heartbeatService.heartbeatReceived(HeartbeatService.HeartbeatSource.INTERNAL, proxy.getId(), null);
			}

		} else {
			log.info("Recovery of running apps disabled");
		}

		isReady = true;
	}

	private void setupPortMapping(Proxy proxy, Container container, ExistingContainerInfo containerInfo) throws Exception {
		// interpret SpEL
		ExpressionAwareContainerSpec eContainerSpec = new ExpressionAwareContainerSpec(container.getSpec(), proxy, expressionResolver);
		container.setSpec(eContainerSpec);
		containerBackend.setupPortMappingExistingProxy(proxy, container, containerInfo.getPortBindings());
		container.setSpec(eContainerSpec);
	}

	public boolean isReady() {
        return isReady;
	}

	/**
	 * Checks whether the proxy with the given instanceId may be recovered in this ShinyProxy server.
	 */
	public Boolean canRecoverProxy(String containerInstanceId) {
		if (containerInstanceId == null) {
			// sanity check
			return false;
		}

		if (recoverFromDifferentConfig) {
			// always allow to recover
			return true;
		}

		// only allow if instanceId is equal to the current instanceId
		return containerInstanceId.equals(identifierService.instanceId);
	}

}
