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

    protected final O originalValue;

    protected final R value;

    protected final boolean resolved;

    public SpelField(O originalValue) {
        this.originalValue = originalValue;
        this.value = null;
        resolved = false;
    }

    private SpelField(O originalValue, R value) {
        this.originalValue = originalValue;
        this.value = value;
        this.resolved = true;
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

    public abstract SpelField<O,R> resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext);

    public static class String extends SpelField<java.lang.String, java.lang.String> {

        public String(java.lang.String originalValue) {
            super(originalValue);
        }

        public String() {
            super(null);
        }

        private String(java.lang.String originalValue, java.lang.String value) {
            super(originalValue, value);
        }

        @Override
        public SpelField.String resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            if (resolved) {
                throw new IllegalStateException("Trying to resolve a SpelField which is already resolved.");
            }
            if (originalValue == null) {
                return new SpelField.String(null, null);
            }
            return new SpelField.String(originalValue, specExpressionResolver.evaluateToString(originalValue, specExpressionContext));
        }

    }

    public static class Long extends SpelField<java.lang.String, java.lang.Long> {

        public Long(java.lang.String originalValue) {
            super(originalValue);
        }

        public Long() {
            super(null);
        }

        private Long(java.lang.String originalValue, java.lang.Long value) {
            super(originalValue, value);
        }

        @Override
        public SpelField.Long resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            if (resolved) {
                throw new IllegalStateException("Trying to resolve a SpelField which is already resolved.");
            }
            if (originalValue == null) {
                return new SpelField.Long(null, null);
            }
            return new SpelField.Long(originalValue, specExpressionResolver.evaluateToLong(originalValue, specExpressionContext));
        }

    }

    public static class StringList extends SpelField<List<java.lang.String>, List<java.lang.String>> {

        public StringList(List<java.lang.String> originalValue) {
            super(originalValue);
        }

        public StringList() {
            super(null);
        }

        private StringList(List<java.lang.String> originalValue, List<java.lang.String> value) {
            super(originalValue, value);
        }

        @Override
        public SpelField.StringList resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            if (resolved) {
                throw new IllegalStateException("Trying to resolve a SpelField which is already resolved.");
            }
            if (originalValue == null) {
                return new SpelField.StringList(null, null);
            }
            // TODO implementation is different from ExpressionAwareContainerSpec::resolve
            return new SpelField.StringList(originalValue, specExpressionResolver.evaluateToList(originalValue, specExpressionContext));
        }

        /**
         * Adds a value to the list.
         * @param newValue
         */
        public void add(java.lang.String newValue) {
            if (!resolved) {
                throw new IllegalStateException("Trying to resolve a SpelField which is already resolved.");
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

        private StringMap(Map<java.lang.String, java.lang.String> originalValue, Map<java.lang.String, java.lang.String> value) {
            super(originalValue, value);
        }

        @Override
        public SpelField.StringMap resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            if (resolved) {
                throw new IllegalStateException("Trying to resolve a SpelField which is already resolved.");
            }
            if (originalValue == null) {
                return new SpelField.StringMap(null, null);
            }

            Map<java.lang.String, java.lang.String> newValue = new HashMap<>();
            originalValue.forEach((key, mapValue) -> {
                newValue.put(key, specExpressionResolver.evaluateToString(mapValue, specExpressionContext));
            });
            return new SpelField.StringMap(originalValue, newValue);
        }

    }
}
