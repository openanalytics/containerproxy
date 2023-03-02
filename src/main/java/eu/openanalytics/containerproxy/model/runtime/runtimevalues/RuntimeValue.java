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

import java.util.Objects;

/**
 * POJO containing a `String` value and a `RuntimeValueKey` key. This class is used to effectively store the runtime
 * values
 */
public class RuntimeValue {

    private final RuntimeValueKey<?> key;

    private final Object value;

    public RuntimeValue(RuntimeValueKey<?> key, Object value) {
        if (!key.isInstance(value)) {
            throw new IllegalArgumentException("Provided value is not of the correct type!");
        }
        this.key = Objects.requireNonNull(key, "key may not be null");
        this.value = Objects.requireNonNull(value, "value may not be null for key " + key.getKeyAsEnvVar());
    }

    public RuntimeValueKey<?> getKey() {
        return key;
    }

    /**
     * ToString is only used for debugging/logging purposes, should not be used to work with the value in the code.
     */
    @Override
    public String toString() {
        return key.serializeToString(getObject());
    }

    public <T> T getObject() {
        return (T) getObject(key.getClazz());
    }

    private <T> T getObject(Class<T> clazz) {
        if (!clazz.isInstance(value)) {
            throw new RuntimeException("Cannot convert RuntimeObject to the desired type!");
        }
        return clazz.cast(value);
    }

}
