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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import eu.openanalytics.containerproxy.model.runtime.ExistingContainerInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

/**
 * Service to recover running apps after restart.
 * This service does not contain code for specific backends, instead it delegates these parts to the
 * scanExistingContainers and setupPortMappingExistingProxy methods of the container backends.
 */
@Service
public class AppRecoveryService {

	protected static final String PROPERTY_RECOVER_RUNNING_APPS = "proxy.recover_running_apps";

	private Logger log = LogManager.getLogger(AppRecoveryService.class);

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

	private boolean isReady = false;

	@EventListener(ApplicationReadyEvent.class)
	public void recoverRunningApps() throws Exception {
		if (Boolean.parseBoolean(environment.getProperty(PROPERTY_RECOVER_RUNNING_APPS, "false"))) {
			log.info("Recovery of running apps enabled");

			Map<String, Proxy> proxies = new HashMap();

			for (ExistingContainerInfo containerInfo: containerBackend.scanExistingContainers()) {
				if (!proxies.containsKey(containerInfo.getProxyId())) {
					ProxySpec proxySpec = proxySpecProvider.getSpec(containerInfo.getProxySpecId());
					if (proxySpec == null) {
						log.warn(String.format("Found existing container (%s) but not corresponding proxy spec.", containerInfo.getContainerId()));
						continue;
					}
					Proxy proxy = new Proxy();
					proxy.setId(containerInfo.getProxyId());
					proxy.setSpec(proxySpec);
					proxy.setStatus(ProxyStatus.Stopped);
					proxy.setStartupTimestamp(containerInfo.getStartupTimestamp());
					proxy.setUserId(containerInfo.getUserId());
					proxies.put(containerInfo.getProxyId(), proxy);
				}
				Proxy proxy = proxies.get(containerInfo.getProxyId());
				Container container = new Container();
				container.setId(containerInfo.getContainerId());
				container.setParameters(containerInfo.getParameters());
				container.setSpec(proxy.getSpec().getContainerSpec(containerInfo.getImage()));
				proxy.addContainer(container);
				proxy.setStatus(ProxyStatus.Up);

				containerBackend.setupPortMappingExistingProxy(proxy, container, containerInfo.getPortBindings());
			}

			for (Proxy proxy: proxies.values()) {
				proxyService.addExistingProxy(proxy);
				heartbeatService.heartbeatReceived(proxy.getId());
			}

		} else {
			log.info("Recovery of running apps disabled");
		}

		isReady = true;
	}

	public boolean isReady() {
		return isReady;
	}

}
