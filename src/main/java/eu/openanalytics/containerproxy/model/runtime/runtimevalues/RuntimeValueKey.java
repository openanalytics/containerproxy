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
 * Contains all metadata for a given runtime-value.
 * Each implementation of this class should have a private constructor and a `public static` field called `inst`
 * containing the singleton of this key.
 */
public abstract class RuntimeValueKey<T> {

    private final String keyAsLabel;
    private final String keyAsEnvVar;

    private final Boolean includeAsLabel;

    private final Boolean includeAsAnnotation;

    private final Boolean includeAsEnvironmentVariable;

    private final Boolean includeInApi;

    private final Boolean isContainerSpecific;

    private final Boolean isRequired;

    private final Class<T> clazz;

    public RuntimeValueKey(String keyAsLabel, String keyAsEnvVar, Boolean includeAsLabel, Boolean includeAsAnnotation, Boolean includeAsEnvironmentVariable, Boolean includeInApi, Boolean isRequired, Boolean isContainerSpecific, Class<T> clazz) {
        this.keyAsLabel = Objects.requireNonNull(keyAsLabel, "keyAsLabel may not be null");
        this.keyAsEnvVar = Objects.requireNonNull(keyAsEnvVar, "keyAsEnvVar may not be null");
        this.includeAsLabel = includeAsLabel;
        this.includeAsAnnotation = includeAsAnnotation;
        this.includeAsEnvironmentVariable = includeAsEnvironmentVariable;
        this.includeInApi = includeInApi;
        this.isContainerSpecific = isContainerSpecific;
        this.isRequired = isRequired;
        this.clazz = clazz;
    }

    public String getKeyAsLabel() {
        return keyAsLabel;
    }

    public String getKeyAsEnvVar() {
        return keyAsEnvVar;
    }

    public Boolean getIncludeAsLabel() {
        return includeAsLabel;
    }

    public Boolean getIncludeAsAnnotation() {
        return includeAsAnnotation;
    }

    public Boolean getIncludeAsEnvironmentVariable() {
        return includeAsEnvironmentVariable;
    }

    public Boolean getIncludeInApi() {
        return includeInApi;
    }

    public Boolean isContainerSpecific() {
        return isContainerSpecific;
    }

    public Boolean isRequired() {
        return isRequired;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public boolean isInstance(Object object) {
        return clazz.isInstance(object);
    }

    public abstract T deserializeFromString(String value);

    public abstract String serializeToString(T value);

    /**
     * ToString is only used for debugging/logging purposes, should not be used to work with the value in the code.
     */
    @Override
    public String toString() {
        return keyAsEnvVar;
    }

}
