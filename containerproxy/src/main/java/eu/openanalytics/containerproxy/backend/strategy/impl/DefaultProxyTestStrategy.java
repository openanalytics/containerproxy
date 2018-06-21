/**
 * ContainerProxy
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
package eu.openanalytics.containerproxy.backend.strategy.impl;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.util.Retrying;

@Component
public class DefaultProxyTestStrategy implements IProxyTestStrategy {

	protected static final String PROPERTY_CONTAINER_WAIT_TIME = "container-wait-time";
	protected static final String PROPERTY_CONTAINER_WAIT_TIMEOUT = "container-wait-timeout";
	
	protected final Logger log = LogManager.getLogger(getClass());
	
	@Override
	public boolean testProxy(Proxy proxy) {
		if (proxy.getTargets().isEmpty()) return true;
		
		Set<String> mappings = proxy.getTargets().keySet();
		String defaultMapping = mappings.stream().filter(m -> !m.contains("/")).findAny().orElse(mappings.iterator().next());
		String testURL = proxy.getTargets().get(defaultMapping).toString();
		if (testURL == null || testURL.isEmpty()) return true;
		
		//TODO
		int totalWaitMs = 20000; //Integer.valueOf(getProperty(PROPERTY_CONTAINER_WAIT_TIME, "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		int timeoutMs = 5000; //Integer.parseInt(getProperty(PROPERTY_CONTAINER_WAIT_TIMEOUT, "5000"));
		
		return Retrying.retry(i -> {
			try {
				HttpURLConnection connection = ((HttpURLConnection) new URL(testURL).openConnection());
				connection.setConnectTimeout(timeoutMs);
				int responseCode = connection.getResponseCode();
				if (responseCode == 200) return true;
			} catch (Exception e) {
				if (i > 1) log.warn(String.format("Container unresponsive, trying again (%d/%d): %s", i, maxTries, testURL));
			}
			return false;
		}, maxTries, waitMs);
	}

}
