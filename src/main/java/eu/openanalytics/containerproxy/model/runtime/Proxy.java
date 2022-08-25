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
package eu.openanalytics.containerproxy.model.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ProxySpecIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueStore;
import net.minidev.json.annotate.JsonIgnore;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Proxy extends RuntimeValueStore {

	private String id;
	
	private ProxyStatus status;

	private long startupTimestamp;
	private long createdTimestamp;
	private String userId;

	private List<Container> containers;
	private Map<String,URI> targets;

	public Proxy() {
		containers = new ArrayList<>();
		targets = new HashMap<>();
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ProxyStatus getStatus() {
		return status;
	}

	public void setStatus(ProxyStatus status) {
		this.status = status;
	}

	public long getStartupTimestamp() {
		return startupTimestamp;
	}

	public void setStartupTimestamp(long startupTimestamp) {
		this.startupTimestamp = startupTimestamp;
	}

	public long getCreatedTimestamp() {
		return createdTimestamp;
	}

	public void setCreatedTimestamp(long createdTimestamp) {
		this.createdTimestamp = createdTimestamp;
	}

	public String getUserId() {
		return userId;
	}

	public String getSpecId() {
		return getRuntimeValue(ProxySpecIdKey.inst);
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public List<Container> getContainers() {
		return containers;
	}
	
	public void setContainers(List<Container> containers) {
		this.containers = containers;
	}
	
	public void addContainer(Container container) {
		this.containers.add(container);
	}
	
	public Map<String, URI> getTargets() {
		return targets;
	}
	
	public void setTargets(Map<String, URI> targets) {
		this.targets = targets;
	}

}
