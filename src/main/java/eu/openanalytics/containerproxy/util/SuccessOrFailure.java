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
package eu.openanalytics.containerproxy.util;

/**
 * Helper class to indicate a Success or Failure of an operation and return a value at the same tim.e
 * @param <T>
 */
public class SuccessOrFailure<T> {

    private final boolean success;
    private final T value;
    private final String message;
    private final Throwable throwable;

    private SuccessOrFailure(boolean success, T value, String message, Throwable throwable) {
        this.success = success;
        this.value = value;
        this.message = message;
        this.throwable = throwable;
    }

    static public <T> SuccessOrFailure<T> createSuccess(T value) {
        return new SuccessOrFailure<>(true, value, "", null);
    }

    static public <T> SuccessOrFailure<T> createFailure(T value, String message, Throwable throwable) {
        return new SuccessOrFailure<>(false, value, message, throwable);
    }

    static public <T> SuccessOrFailure<T> createFailure(T value, String message) {
        return new SuccessOrFailure<>(false, value, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public T getValue() {
        return value;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getThrowable() {
        return throwable;
    }

}
