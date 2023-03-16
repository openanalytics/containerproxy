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

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

public class SpelFieldConverters {

    @Component
    @ConfigurationPropertiesBinding
    public static class FloatToSpelFieldStringConvertor implements Converter<Float, SpelField.String> {
        @Override
        public SpelField.String convert(@Nonnull Float source) {
            return new SpelField.String(source.toString());
        }
    }

    @Component
    @ConfigurationPropertiesBinding
    public static class DoubleToSpelFieldStringConvertor implements Converter<Double, SpelField.String> {
        @Override
        public SpelField.String convert(@Nonnull Double source) {
            return new SpelField.String(source.toString());
        }
    }

    @Component
    @ConfigurationPropertiesBinding
    public static class IntegerToSpelFieldStringConvertor implements Converter<Integer, SpelField.String> {
        @Override
        public SpelField.String convert(@Nonnull Integer source) {
            return new SpelField.String(source.toString());
        }
    }

    @Component
    @ConfigurationPropertiesBinding
    public static class LongToSpelFieldStringConvertor implements Converter<Long, SpelField.String> {
        @Override
        public SpelField.String convert(@Nonnull Long source) {
            return new SpelField.String(source.toString());
        }
    }

}
