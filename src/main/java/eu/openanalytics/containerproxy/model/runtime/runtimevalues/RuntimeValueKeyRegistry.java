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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps track of the `RuntimeValueKey` instances so that they can be listed and user in e.g. the App Recovery system.
 */
public class RuntimeValueKeyRegistry {

    private static final Map<String, RuntimeValueKey<?>> KEYS = new HashMap<>();

    public static void addRuntimeValueKey(RuntimeValueKey<?> key) {
        if (!KEYS.containsKey(key.getKeyAsEnvVar())) {
            KEYS.put(key.getKeyAsEnvVar(), key);
        } else {
            throw new IllegalStateException("Cannot add duplicate RuntimeValueKey with name " + key.getKeyAsEnvVar());
        }
    }

    public static Collection<RuntimeValueKey<?>> getRuntimeValueKeys() {
        return KEYS.values();
    }

    public static RuntimeValueKey<?> getRuntimeValue(String key) {
        if (!KEYS.containsKey(key)) {
            throw new IllegalArgumentException("Could not find RuntimeValueKey using key " + key);
        }
        return KEYS.get(key);
    }

    static {
        addRuntimeValueKey(CreatedTimestampKey.inst);
        addRuntimeValueKey(InstanceIdKey.inst);
        addRuntimeValueKey(ProxiedAppKey.inst);
        addRuntimeValueKey(ProxyIdKey.inst);
        addRuntimeValueKey(ProxySpecIdKey.inst);
        addRuntimeValueKey(RealmIdKey.inst);
        addRuntimeValueKey(UserGroupsKey.inst);
        addRuntimeValueKey(UserIdKey.inst);
        addRuntimeValueKey(ParameterNamesKey.inst);
        addRuntimeValueKey(ParameterValuesKey.inst);
        addRuntimeValueKey(HeartbeatTimeoutKey.inst);
        addRuntimeValueKey(MaxLifetimeKey.inst);
        addRuntimeValueKey(ContainerIndexKey.inst);
        addRuntimeValueKey(ContainerImageKey.inst);
        addRuntimeValueKey(BackendContainerNameKey.inst);
        addRuntimeValueKey(PortMappingsKey.inst);
        addRuntimeValueKey(DisplayNameKey.inst);
    }

}
