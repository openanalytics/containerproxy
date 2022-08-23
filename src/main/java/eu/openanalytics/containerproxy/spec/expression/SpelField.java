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
package eu.openanalytics.containerproxy.spec.expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class SpelField<O, R> {

    protected O originalValue;

    protected R value = null;

    protected boolean resolved = false;

    public SpelField(O originalValue) {
        this.originalValue = originalValue;
    }

    public R getValue() {
        if (!resolved) {
            throw new IllegalStateException("Trying to retrieve a SpelField value which is not yet resolved.");
        }
        if (originalValue == null) {
            throw new IllegalStateException("Trying to retrieve a value which is null.");
        }
        return value;
    }

    public R getValueOrNull() {
        if (!resolved) {
            throw new IllegalStateException("Trying to retrieve a SpelField value which is not yet resolved.");
        }
        return value;
    }

    public R getValueOrDefault(R defaultValue) {
        if (!resolved) {
            throw new IllegalStateException("Trying to retrieve a SpelField value which is not yet resolved.");
        }
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public boolean isPresent() {
        if (!resolved) {
            throw new IllegalStateException("Trying to retrieve a SpelField value which is not yet resolved.");
        }
        return value != null;
    }

    public void ifPresent(Consumer<? super R> block) {
        if (!resolved) {
            throw new IllegalStateException("Trying to retrieve a SpelField value which is not yet resolved.");
        }
        if (value != null) {
            block.accept(value);
        }
    }

    public <U> U mapOrNull(Function<? super R, ? extends U> block) {
        if (!resolved) {
            throw new IllegalStateException("Trying to retrieve a SpelField value which is not yet resolved.");
        }
        if (value == null) {
            return null;
        }
        return block.apply(value);
    }

    public O getOriginalValue() {
        return originalValue;
    }

    public void resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
        if (resolved) {
            throw new IllegalStateException("Trying to resolve a SpelField which is already resolved.");
        }
        if (originalValue == null) {
            value = null;
        } else {
            doResolve(specExpressionResolver, specExpressionContext);
        }
        resolved = true;
    }

    protected abstract void doResolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext);

    public static class String extends SpelField<java.lang.String, java.lang.String> {

        public String(java.lang.String originalValue) {
            super(originalValue);
        }

        public String() {
            super(null);
        }

        @Override
        public void doResolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            value = specExpressionResolver.evaluateToString(originalValue, specExpressionContext);
        }

    }

    public static class Long extends SpelField<java.lang.String, java.lang.Long> {

        public Long(java.lang.String originalValue) {
            super(originalValue);
        }

        public Long() {
            super(null);
        }

        @Override
        public void doResolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            value = specExpressionResolver.evaluateToLong(originalValue, specExpressionContext);
        }

    }

    public static class StringList extends SpelField<List<java.lang.String>, List<java.lang.String>> {

        public StringList(List<java.lang.String> originalValue) {
            super(originalValue);
        }

        public StringList() {
            super(null);
        }

        @Override
        public void doResolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            // TODO implementation is different from ExpressionAwareContainerSpec::resolve
            value = specExpressionResolver.evaluateToList(originalValue, specExpressionContext);
        }

        /**
         * Adds a value to the list.
         * @param newValue
         */
        public void add(java.lang.String newValue) {
            if (value == null) {
                value = new ArrayList<>();
            }
            value.add(newValue);
        }

    }

    public static class StringMap extends SpelField<Map<java.lang.String, java.lang.String>, Map<java.lang.String, java.lang.String>> {

        public StringMap(Map<java.lang.String, java.lang.String> originalValue) {
            super(originalValue);
        }

        public StringMap() {
            super(null);
        }

        @Override
        public void doResolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            value = new HashMap<>();
            originalValue.forEach((key, mapValue) -> {
                value.put(key, specExpressionResolver.evaluateToString(mapValue, specExpressionContext));
            });
        }

    }
}
