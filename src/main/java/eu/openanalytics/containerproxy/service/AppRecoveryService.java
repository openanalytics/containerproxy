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

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerIndexKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CreatedTimestampKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.DisplayNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxyIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxySpecIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.UserIdKey;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service to recover running apps after restart.
 * This service does not contain code for specific backends, instead it delegates these parts to the
 * scanExistingContainers and setupPortMappingExistingProxy methods of the container backends.
 */
@Service
public class AppRecoveryService {

	public static final String PROPERTY_RECOVER_RUNNING_PROXIES = "proxy.recover-running-proxies";
	public static final String PROPERTY_RECOVER_RUNNING_PROXIES_FROM_DIFFERENT_CONFIG = "proxy.recover-running-proxies-from-different-config";

	private final Logger log = LogManager.getLogger(AppRecoveryService.class);

	@Inject
	private Environment environment;

	@Inject
	private IContainerBackend containerBackend;

	@Inject
	private ProxyService proxyService;

	@Inject
	private HeartbeatService heartbeatService;

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

			Map<String, Proxy.ProxyBuilder> proxies = new HashMap<>();

			for (ExistingContainerInfo containerInfo: containerBackend.scanExistingContainers()) {
			    String proxyId = containerInfo.getRuntimeValue(ProxyIdKey.inst).getObject();

				if (!proxies.containsKey(proxyId)) {
					Proxy.ProxyBuilder proxy = Proxy.builder();
					proxy.id(proxyId);
					proxy.specId(containerInfo.getRuntimeValue(ProxySpecIdKey.inst).getObject());
					proxy.status(ProxyStatus.Stopped);
					long createdTimestamp = Long.parseLong(containerInfo.getRuntimeValue(CreatedTimestampKey.inst).getObject());
					proxy.createdTimestamp(createdTimestamp);
					// we cannot store the startUpTimestamp in the ContainerBackend, therefore when recovering apps
					// we set the startUpTimestamp to the time the proxy was created. The distinction between created
					// and started is only important for the events (e.g. Prometheus) not for the whole application.
					proxy.startupTimestamp(createdTimestamp);
					proxy.userId(containerInfo.getRuntimeValue(UserIdKey.inst).getObject());
					proxy.displayName(containerInfo.getRuntimeValue(DisplayNameKey.inst).getObject());

					proxy.addRuntimeValues(containerInfo.getRuntimeValues()
							.values()
							.stream()
							.filter(r -> !r.getKey().isContainerSpecific())
							.collect(Collectors.toList())
					);

					proxies.put(proxyId, proxy);
				}
				Proxy.ProxyBuilder proxy = proxies.get(proxyId);
				Container.ContainerBuilder containerBuilder = Container.builder();
				containerBuilder.id(containerInfo.getContainerId());
				containerBuilder.addRuntimeValues(containerInfo.getRuntimeValues()
						.values()
						.stream()
						.filter(r -> r.getKey().isContainerSpecific())
						.collect(Collectors.toList())
				);
				containerBuilder.index(containerInfo.getRuntimeValue(ContainerIndexKey.inst).getObject());

				Container container = containerBuilder.build();
				if (containerInfo.getProxyStatus() != null) {
					proxy.status(containerInfo.getProxyStatus());
				} else {
					proxy.status(ProxyStatus.Up);
				}
				container = containerBackend.setupPortMappingExistingProxy(proxy.build(), container, containerInfo.getPortBindings());
				proxy.addContainer(container);
			}

			for (Proxy.ProxyBuilder proxyBuilder: proxies.values()) {
				Proxy proxy = proxyBuilder.build();
				proxyService.addExistingProxy(proxy);
				heartbeatService.heartbeatReceived(HeartbeatService.HeartbeatSource.INTERNAL, proxy.getId(), null);
			}

		} else {
			log.info("Recovery of running apps disabled");
		}

		isReady = true;
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
