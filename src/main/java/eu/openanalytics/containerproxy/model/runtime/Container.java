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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@AllArgsConstructor
@JsonView(Views.Default.class)
public class Container extends RuntimeValueStore {

    /**
     * Index in the array of ContainerSpecs of the ProxySpec.
     */
    Integer index;
    String id;

    Map<String, URI> targets;
    Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues;

    @JsonCreator
    public static Container createFromJson(@JsonProperty("index") Integer index,
                                           @JsonProperty("id") String id,
                                           @JsonProperty("targets") Map<String, URI> targets,
                                           @JsonProperty("_runtimeValues") Map<String, String> runtimeValues) {

        Container.ContainerBuilder builder = Container.builder()
                .index(index)
                .id(id)
                .targets(targets);

        for (Map.Entry<String, String> runtimeValue : runtimeValues.entrySet()) {
            RuntimeValueKey<?> key = RuntimeValueKeyRegistry.getRuntimeValue(runtimeValue.getKey());
            builder.addRuntimeValue(new RuntimeValue(key, key.deserializeFromString(runtimeValue.getValue())), false);
        }

        return builder.build();
    }

    @JsonView(Views.Internal.class)
    public Map<String, URI> getTargets() {
        if (targets == null) {
            return Collections.unmodifiableMap(new HashMap<>());
        }
        return Collections.unmodifiableMap(targets);
    }

    @Override
    public Map<RuntimeValueKey<?>, RuntimeValue> getRuntimeValues() {
        if (runtimeValues == null) {
            return Collections.unmodifiableMap(new HashMap<>());
        }
        return Collections.unmodifiableMap(runtimeValues);
    }

    public static class ContainerBuilder {

        public ContainerBuilder runtimeValues(Map<RuntimeValueKey<?>, RuntimeValue> runtimeValues) {
            // take a copy of the map so that when using toBuilder a deep copy is returned
            // otherwise, copies will have the same underlying map
            if (runtimeValues != null) {
                this.runtimeValues = new HashMap<>(runtimeValues);
            }
            return this;
        }

        public ContainerBuilder targets(Map<String, URI> targets) {
            // take a copy of the map so that when using toBuilder a deep copy is returned
            // otherwise, copies will have the same underlying map
            if (targets != null) {
                this.targets = new HashMap<>(targets);
            }
            return this;
        }

        public Container.ContainerBuilder addRuntimeValue(RuntimeValue runtimeValue, boolean override) {
            if (this.runtimeValues == null) {
                this.runtimeValues = new HashMap<>();
            }
            if (!this.runtimeValues.containsKey(runtimeValue.getKey()) || override) {
                this.runtimeValues.put(runtimeValue.getKey(), runtimeValue);
            }
            return this;
        }

        public Container.ContainerBuilder addRuntimeValues(List<RuntimeValue> runtimeValues) {
            for (RuntimeValue runtimeValue: runtimeValues) {
                addRuntimeValue(runtimeValue, false);
            }
            return this;
        }

        public Container.ContainerBuilder addTarget(String mapping, URI target) {
            if (targets == null) {
                targets = new HashMap<>();
            }
            targets.put(mapping, target);
            return this;
        }

    }

}
