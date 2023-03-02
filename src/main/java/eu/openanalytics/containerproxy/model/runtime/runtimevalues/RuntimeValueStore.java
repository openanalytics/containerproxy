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
package eu.openanalytics.containerproxy.model.runtime.runtimevalues;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.model.Views;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class RuntimeValueStore {

    @JsonIgnore
    protected abstract Map<RuntimeValueKey<?>, RuntimeValue> getRuntimeValues();

    @JsonView(Views.UserApi.class)
    @JsonProperty("runtimeValues")
    public Map<String, Object> getRuntimeValuesJson() {
        // only output key<->value in JSON
        Map<String, Object> result = new HashMap<>();
        for (RuntimeValue value : getRuntimeValues().values()) {
            if (value.getKey().getIncludeInApi()) {
                result.put(value.getKey().getKeyAsEnvVar(), value.getObject());
            }
        }
        return result;
    }

    // TODO use a proper name for the internal representation of runtimeValue or switch to DTOs
    @JsonView(Views.Internal.class)
    @JsonProperty("_runtimeValues")
    public Map<String, Object> geAllRuntimeValuesJson() {
        // only output key<->value in JSON
        Map<String, Object> result = new HashMap<>();
        for (RuntimeValue value : getRuntimeValues().values()) {
            result.put(value.getKey().getKeyAsEnvVar(), value.toString());
        }
        return result;
    }

    /**
     * Used in SpEL of application.yml
     */
    public String getRuntimeValue(String keyAsEnvVar) {
        Objects.requireNonNull(keyAsEnvVar, "key may not be null");
        return getRuntimeValue(RuntimeValueKeyRegistry.getRuntimeValue(keyAsEnvVar));
    }

    public Object getRuntimeObject(String keyAsEnvVar) {
        Objects.requireNonNull(keyAsEnvVar, "key may not be null");
        return getRuntimeObject(RuntimeValueKeyRegistry.getRuntimeValue(keyAsEnvVar));
    }

    public <T> T getRuntimeObject(RuntimeValueKey<T> key) {
        Objects.requireNonNull(key, "key may not be null");
        RuntimeValue runtimeValue = getRuntimeValues().get(key);
        Objects.requireNonNull(runtimeValue, "did not found a value for key " + key.getKeyAsEnvVar());
        return runtimeValue.getObject();
    }

    public <T> T getRuntimeObjectOrNull(RuntimeValueKey<T> key) {
        Objects.requireNonNull(key, "key may not be null");
        RuntimeValue runtimeValue = getRuntimeValues().get(key);
        if (runtimeValue == null) {
            return null;
        }
        return runtimeValue.getObject();
    }

    public <T> T getRuntimeObjectOrDefault(RuntimeValueKey<T> key, T defaultValue) {
        Objects.requireNonNull(key, "key may not be null");
        RuntimeValue runtimeValue = getRuntimeValues().get(key);
        if (runtimeValue == null) {
            return defaultValue;
        }
        return runtimeValue.getObject();
    }

    public <T> String getRuntimeValue(RuntimeValueKey<T> key) {
        Objects.requireNonNull(key, "key may not be null");
        RuntimeValue runtimeValue = getRuntimeValues().get(key);
        Objects.requireNonNull(runtimeValue, "did not found a value for key " + key.getKeyAsEnvVar());
        return key.serializeToString(runtimeValue.getObject());
    }

}
