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
package eu.openanalytics.containerproxy.util;

import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnvironmentUtils {

    public static List<String> readList(Environment environment, String propertyName) {
        String singleValue = environment.getProperty(propertyName);
        if (singleValue != null) {
            return Collections.singletonList(singleValue);
        }

        List<String> result = new ArrayList<>();
        int i = 0;
        String value = environment.getProperty(String.format(propertyName + "[%d]", i));
        while (value != null) {
            result.add(value);
            i++;
            value = environment.getProperty(String.format(propertyName + "[%d]", i));
        }

        if (result.size() == 0) {
            return null;
        }

        return result;
    }

}
