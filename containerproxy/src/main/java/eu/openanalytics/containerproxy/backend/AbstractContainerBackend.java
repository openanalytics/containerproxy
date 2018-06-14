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
package eu.openanalytics.containerproxy.backend;

import java.io.File;
import java.util.function.BiConsumer;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTargetMappingStrategy;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.util.PropertyResolver;

public abstract class AbstractContainerBackend implements IContainerBackend {

	protected static final String PROPERTY_INTERNAL_NETWORKING = "internal-networking";
	
	protected final Logger log = LogManager.getLogger(getClass());
	
	private boolean useInternalNetwork;
	
	@Inject
	protected IProxyTargetMappingStrategy mappingStrategy;

	@Inject
	protected IProxyTestStrategy testStrategy;
	
	@Inject
	protected PropertyResolver props;
	
	@Override
	public void initialize() throws ContainerProxyException {
		// If this application runs as a container itself, things like port publishing can be omitted.
		useInternalNetwork = Boolean.valueOf(getProperty(PROPERTY_INTERNAL_NETWORKING, "false"));
	}
	
	@Autowired(required=false)
	public void setMappingStrategy(IProxyTargetMappingStrategy mappingStrategy) {
		this.mappingStrategy = mappingStrategy;
	}
	
	@Autowired(required=false)
	public void setTestStrategy(IProxyTestStrategy testStrategy) {
		this.testStrategy = testStrategy;
	}
	
	@Override
	public void startProxy(Proxy proxy) throws ContainerProxyException {
		proxy.setStatus(ProxyStatus.Starting);
		
		try {
			doStartProxy(proxy);
		} catch (Throwable t) {
			stopProxy(proxy);
			throw new ContainerProxyException("Failed to start container", t);
		}
		
		if (!testStrategy.testProxy(proxy)) {
			stopProxy(proxy);
			throw new ContainerProxyException("Container did not respond in time");
		}
		
		proxy.setStartupTimestamp(System.currentTimeMillis());
		proxy.setStatus(ProxyStatus.Up);
	}
	
	protected abstract void doStartProxy(Proxy proxy) throws Exception;
	
	@Override
	public void stopProxy(Proxy proxy) throws ContainerProxyException {
		try {
			proxy.setStatus(ProxyStatus.Stopping);
			doStopProxy(proxy);
			proxy.setStatus(ProxyStatus.Stopped);
		} catch (Exception e) {
			throw new ContainerProxyException("Failed to stop container", e);
		}
	}

	protected abstract void doStopProxy(Proxy proxy) throws Exception;
	
	@Override
	public BiConsumer<File, File> getOutputAttacher(Proxy proxy) {
		// Default: do not support output attaching.
		return null;
	}
	
	protected String getProperty(String key) {
		return getProperty(key, null);
	}
	
	protected String getProperty(String key, String defaultValue) {
		return props.get(getPropertyPrefix() + key, defaultValue);
	}
	
	protected abstract String getPropertyPrefix();
	
	protected boolean isUseInternalNetwork() {
		return useInternalNetwork;
	}
}
