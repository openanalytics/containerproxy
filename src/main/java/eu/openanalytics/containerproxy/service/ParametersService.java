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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.model.runtime.AllowedParametersForUser;
import eu.openanalytics.containerproxy.model.runtime.ProvidedParameters;
import eu.openanalytics.containerproxy.model.spec.Parameters;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ParametersService {

    public ProvidedParameters parseAndValidateRequest(ProxySpec resolvedSpec, ProvidedParameters providedParameters) {
        Parameters parameters = resolvedSpec.getParameters();
        if (parameters == null) {
            return new ProvidedParameters();
        }

        // check if all keys are provided
        // TODO check if no other keys are provided
        for (String key : parameters.getIds()) {
            if (!providedParameters.containsKey(key)) {
                throw new IllegalArgumentException("Provided User Parameters does not contain key " + key);
            }
        }

        // TODO
        for (Map<String, List<String>> values : parameters.getValues()) {
            if (isAllowedValue(values, providedParameters)) {
                return providedParameters;
            }
        }

        throw new IllegalArgumentException("Provided User Parameters is not allowed");

    }

    public boolean isAllowedValue(Map<String, List<String>> values, ProvidedParameters providedParameters) {
        for (Map.Entry<String, List<String>> keyWithValue : values.entrySet()) {
            if (!providedParameters.containsKey(keyWithValue.getKey())) {
                return false; // TODO check above that all keys present and no other additional keys
            }
            String providedValue = providedParameters.get(keyWithValue.getKey());
            if (!keyWithValue.getValue().contains(providedValue)) {
                return false;
            }
        }
        return true;
    }

    public AllowedParametersForUser calculateAllowedParametersForUser(ProxySpec proxySpec) {
        Parameters parameters = proxySpec.getParameters();
        if (parameters == null) {
            return new AllowedParametersForUser(new HashMap<>(), new HashSet<>());
        }
        List<String> parameterNames = parameters.getIds();

        // 1. compute a unique (per ParameterName) index for every value
        // mapping of ParameterName to a mapping of an allowed value and its index
        Map<String, Map<String, Integer>> valuesToIndex = new HashMap<>();
        Map<String, List<Pair<Integer, String>>> values = new HashMap<>();
        // for every set of allowed values
        for (Map<String, List<String>> parameterValues : parameters.getValues()) {
            // for every parameter in this set
            for (Map.Entry<String, List<String>> parameterNameToValues : parameterValues.entrySet()) {
                String parameterName = parameterNameToValues.getKey();
                valuesToIndex.computeIfAbsent(parameterName, (k) -> new HashMap<>());
                values.computeIfAbsent(parameterName, (k) -> new ArrayList<>());
                // for every value of this parameter
                for (String value : parameterNameToValues.getValue()) {
                    if (!valuesToIndex.get(parameterName).containsKey(value)) {
                        // add it to allValues if it does not yet exist
                        Integer newIndex = valuesToIndex.get(parameterName).size() + 1;
                        valuesToIndex.get(parameterName).put(value, newIndex);
                        values.get(parameterName).add(Pair.of(newIndex, value));
                    }
                }
            }
        }
        // sort values
        values = values.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        (v) -> v.getValue()
                                .stream().sorted(Comparator.comparingInt(Pair::getKey))
                                .collect(Collectors.toList())));

        // 2. compute the set of allowed values
        HashSet<List<Integer>> allowedCombinations = new HashSet<>();

        // for every value-set
        for (Map<String, List<String>> parameterValues : parameters.getValues()) {
            allowedCombinations.addAll(getAllowedCombinationsForSingleValueSet(parameterNames,
                    parameterValues, valuesToIndex));
        }

        return new AllowedParametersForUser(values, allowedCombinations);

    }

    private List<List<Integer>> getAllowedCombinationsForSingleValueSet(List<String> parameterNames,
                                                                        Map<String, List<String>> parameterValues,
                                                                        Map<String, Map<String, Integer>> valuesToIndex
    ) {
        // start with an empty combination
        List<List<Integer>> newAllowedCombinations = new ArrayList<>();
        newAllowedCombinations.add(new ArrayList<>());

        // for each parameter
        for (String parameterName : parameterNames) {
            // copy the combinations calculated during the previous iteration
            List<List<Integer>> previousAllowedCombinations = new ArrayList<>(newAllowedCombinations);
            newAllowedCombinations = new ArrayList<>();
            // for every allowed value of this parameter
            for (String allowedValue : parameterValues.get(parameterName)) {
                // and for every combination of the previous iteration
                for (List<Integer> combination : previousAllowedCombinations) {
                    // create a new combination with the additional value
                    combination = new ArrayList<>(combination); // copy the previous combination so that we can extend it
                    Integer index = valuesToIndex.get(parameterName).get(allowedValue);
                    combination.add(index);
                    newAllowedCombinations.add(combination);
                }
            }
        }
        return newAllowedCombinations;
    }

}
