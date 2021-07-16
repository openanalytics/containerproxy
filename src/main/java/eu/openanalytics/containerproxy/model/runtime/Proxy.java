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
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import net.minidev.json.annotate.JsonIgnore;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Proxy {

	private String id;
	
	private ProxySpec spec;

	private ProxyStatus status;

	private long startupTimestamp;
	private long createdTimestamp;
	private String userId;

	private List<Container> containers;
	private Map<String,URI> targets;

	private Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues = new HashMap<>();

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

	public ProxySpec getSpec() {
		return spec;
	}

	public void setSpec(ProxySpec spec) {
		this.spec = spec;
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

	@JsonProperty("runtimeValues")
	public Map<String, String> getRuntimeValuesJson() {
	    // only output key<->value in JSON
	    Map<String, String> result = new HashMap<>();
	    for (RuntimeValue value : runtimeValues.values()) {
	    	result.put(value.getKey().getKeyAsEnvVar(), value.getValue());
		}
	    return result;
	}

	@JsonIgnore
	public Map<RuntimeValueKey<?>, RuntimeValue> getRuntimeValues() {
		return runtimeValues;
	}

	@JsonProperty("runtimeValues")
	public void setRuntimeValuesJson(Map<String, String> runtimeValues) {
		for (Map.Entry<String, String> runtimeValue : runtimeValues.entrySet()) {
			RuntimeValueKey<?> key = RuntimeValueKeyRegistry.getRuntimeValue(runtimeValue.getKey());
			RuntimeValue value = new RuntimeValue(key, runtimeValue.getValue());
			this.runtimeValues.put(key, value);
		}
	}

	public void setRuntimeValues(Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues) {
		this.runtimeValues = runtimeValues;
	}

	public void addRuntimeValue(RuntimeValue runtimeValue) {
		if (this.runtimeValues.containsKey(runtimeValue.getKey())) {
			throw new IllegalStateException("Cannot add duplicate label with key " + runtimeValue.getKey().getKeyAsEnvVar());
		} else {
			runtimeValues.put(runtimeValue.getKey(), runtimeValue);
		}
	}

	public void addRuntimeValues(List<RuntimeValue> runtimeValues) {
		for (RuntimeValue runtimeValue: runtimeValues) {
			addRuntimeValue(runtimeValue);
		}
	}

	public void addRuntimeValues(Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues) {
		for (RuntimeValue runtimeValue: runtimeValues.values()) {
			addRuntimeValue(runtimeValue);
		}
	}

	/**
	 * Used in SpEL of application.yml
	 */
	public String getRuntimeValue(String keyAsEnvVar) {
		Objects.requireNonNull(keyAsEnvVar, "key may not be null");
		return getRuntimeValue(RuntimeValueKeyRegistry.getRuntimeValue(keyAsEnvVar));
	}

	public <T> T getRuntimeObject(RuntimeValueKey<T> key) {
		Objects.requireNonNull(key, "key may not be null");
		RuntimeValue runtimeValue = runtimeValues.get(key);
		Objects.requireNonNull(runtimeValue, "did not found a value for key " + key.getKeyAsEnvVar());
		return runtimeValue.getObject();
	}

	public <T> String getRuntimeValue(RuntimeValueKey<T> key) {
		Objects.requireNonNull(key, "key may not be null");
		RuntimeValue runtimeValue = runtimeValues.get(key);
		Objects.requireNonNull(runtimeValue, "did not found a value for key " + key.getKeyAsEnvVar());
		return runtimeValue.getValue();
	}

}
