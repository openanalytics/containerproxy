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
package eu.openanalytics.containerproxy.model.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueStore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@AllArgsConstructor
@JsonView(Views.Default.class)
public class Proxy extends RuntimeValueStore {

	String id;

	@With
	ProxyStatus status;

	long startupTimestamp;
	long createdTimestamp;

	String userId;
	String specId;

	String displayName;

	List<Container> containers;

	Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues;

	@JsonCreator
	public static Proxy createFromJson(@JsonProperty("id") String id,
									   @JsonProperty("status") ProxyStatus status,
									   @JsonProperty("startupTimestamp") long startupTimestamp,
									   @JsonProperty("createdTimestamp") long createdTimestamp,
									   @JsonProperty("userId") String userId,
									   @JsonProperty("specId") String specId,
									   @JsonProperty("displayName") String displayName,
									   @JsonProperty("containers") List<Container> containers,
									   @JsonProperty("_runtimeValues") Map<String, String> runtimeValues) {

		Proxy.ProxyBuilder builder = Proxy.builder()
				.id(id)
				.status(status)
				.startupTimestamp(startupTimestamp)
				.createdTimestamp(createdTimestamp)
				.userId(userId)
				.specId(specId)
				.displayName(displayName)
				.containers(containers);

		for (Map.Entry<String, String> runtimeValue : runtimeValues.entrySet()) {
			RuntimeValueKey<?> key = RuntimeValueKeyRegistry.getRuntimeValue(runtimeValue.getKey());
			builder.addRuntimeValue(new RuntimeValue(key, key.deserializeFromString(runtimeValue.getValue())), false);
		}

		return builder.build();
	}

	public Container getContainer(Integer containerIndex) {
		return containers.stream()
				.filter(c -> Objects.equals(c.getIndex(), containerIndex))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No container found with this index"));
	}

	public List<Container> getContainers() {
		if (containers == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(containers);
	}

	public Map<RuntimeValueKey<?>, RuntimeValue> getRuntimeValues() {
		if (runtimeValues == null) {
			return Collections.unmodifiableMap(new HashMap<>());
		}
		return Collections.unmodifiableMap(runtimeValues);
	}

	@JsonIgnore
	public Map<String, URI> getTargets() {
		return getContainers()
				.stream()
				.flatMap(c -> c.getTargets().entrySet().stream())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	public static class ProxyBuilder {

		public ProxyBuilder runtimeValues(Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues) {
			// take a copy of the map so that when using toBuilder a deep copy is returned
			// otherwise, copies will have the same underlying map
			if (runtimeValues != null) {
				this.runtimeValues = new HashMap<>(runtimeValues);
			}
			return this;
		}

		public ProxyBuilder containers(List<Container> containers) {
			// take a copy of the list so that when using toBuilder a deep copy is returned
			// otherwise, copies will have the same underlying list
			if (containers != null) {
				this.containers = new ArrayList<>(containers);
			}
			return this;
		}

		public ProxyBuilder addRuntimeValue(RuntimeValue runtimeValue, boolean override) {
			if (this.runtimeValues == null) {
				this.runtimeValues = new HashMap<>();
			}
			if (!this.runtimeValues.containsKey(runtimeValue.getKey()) || override) {
				this.runtimeValues.put(runtimeValue.getKey(), runtimeValue);
			}
			return this;
		}

		public ProxyBuilder addRuntimeValues(List<RuntimeValue> runtimeValues) {
			for (RuntimeValue runtimeValue: runtimeValues) {
				addRuntimeValue(runtimeValue, false);
			}
			return this;
		}

		public ProxyBuilder addContainer(Container container) {
			if (containers == null) {
				containers = new ArrayList<>();
			}
			containers.add(container);
			return this;
		}

	}

}
