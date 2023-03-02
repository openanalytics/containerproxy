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
package eu.openanalytics.containerproxy.spec.expression;

import com.fasterxml.jackson.annotation.JsonValue;

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

    @JsonValue
    public Object toJson() {
        if (!resolved) {
            return originalValue;
        }
        return value;
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

    /**
     * Can be used in SPeL expressions.
     *
     * @return string representation of the resolved value
     */
    @Override
    public java.lang.String toString() {
        R res = getValueOrNull();
        if (res == null) {
            return null;
        }
        return res.toString();
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

        public Long(java.lang.Integer originalValue) {
            super(originalValue.toString());
        }

        public Long(java.lang.Long originalValue) {
            super(originalValue.toString());
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

    public static class Integer extends SpelField<java.lang.String, java.lang.Integer> {

        public Integer(java.lang.String originalValue) {
            super(originalValue);
        }

        public Integer(java.lang.Integer originalValue) {
            super(originalValue.toString());
        }

        public Integer() {
            super(null);
        }

        private Integer(java.lang.String originalValue, java.lang.Integer value) {
            super(originalValue, value);
        }

        @Override
        public SpelField.Integer resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            if (resolved) {
                throw new IllegalStateException("Trying to resolve a SpelField which is already resolved.");
            }
            if (originalValue == null) {
                return new SpelField.Integer(null, null);
            }
            return new SpelField.Integer(originalValue, specExpressionResolver.evaluateToInteger(originalValue, specExpressionContext));
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

    public static class Boolean extends SpelField<java.lang.String, java.lang.Boolean> {

        public Boolean(java.lang.String originalValue) {
            super(originalValue);
        }

        public Boolean(java.lang.Boolean originalValue) {
            super(originalValue.toString());
        }

        public Boolean() {
            super(null);
        }

        private Boolean(java.lang.String originalValue, java.lang.Boolean value) {
            super(originalValue, value);
        }

        @Override
        public SpelField.Boolean resolve(SpecExpressionResolver specExpressionResolver, SpecExpressionContext specExpressionContext) {
            if (resolved) {
                throw new IllegalStateException("Trying to resolve a SpelField which is already resolved.");
            }
            if (originalValue == null) {
                return new SpelField.Boolean(null, null);
            }
            return new SpelField.Boolean(originalValue, specExpressionResolver.evaluateToBoolean(originalValue, specExpressionContext));
        }

    }
}
