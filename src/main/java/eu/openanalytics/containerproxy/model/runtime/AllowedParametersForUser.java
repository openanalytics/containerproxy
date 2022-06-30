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
package eu.openanalytics.containerproxy.model.runtime;

import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class AllowedParametersForUser {

    /**
     * Mapping of ParameterName to a list of allowed values.
     * The integer represents the index of the parameter.
     * The list is sorted by the index.
     */
    private final Map<String, List<Pair<Integer, String>>> values;

    /**
     * List of all allowed combinations of the parameters for this specific user.
     */
    private final HashSet<List<Integer>> allowedCombinations;


    public AllowedParametersForUser(Map<String, List<Pair<Integer, String>>> values, HashSet<List<Integer>> allowedCombinations) {
        this.values = values;
        this.allowedCombinations = allowedCombinations;
    }

    public HashSet<List<Integer>> getAllowedCombinations() {
        return allowedCombinations;
    }

    public Map<String, List<Pair<Integer, String>>> getValues() {
        return values;
    }
}
