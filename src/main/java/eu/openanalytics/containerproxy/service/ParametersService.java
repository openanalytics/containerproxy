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
package eu.openanalytics.containerproxy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.model.runtime.AllowedParametersForUser;
import eu.openanalytics.containerproxy.model.runtime.ParameterValues;
import eu.openanalytics.containerproxy.model.runtime.ParameterNames;
import eu.openanalytics.containerproxy.model.spec.ParameterDefinition;
import eu.openanalytics.containerproxy.model.spec.Parameters;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.util.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ParametersService {

    private final IProxySpecProvider baseSpecProvider;

    private final AccessControlEvaluationService accessControlEvaluationService;

    private static final Pattern PARAMETER_ID_PATTERN = Pattern.compile("[a-zA-Z\\d_-]*");

    public ParametersService(IProxySpecProvider baseSpecProvider, AccessControlEvaluationService accessControlEvaluationService, ObjectMapper objectMapper) {
        this.baseSpecProvider = baseSpecProvider;
        this.accessControlEvaluationService = accessControlEvaluationService;
    }

    @PostConstruct
    public void init() {
        for (ProxySpec spec : baseSpecProvider.getSpecs()) {
            validateSpec(spec);
        }
    }

    private void validateSpec(ProxySpec spec) {
        if (spec.getParameters() == null) {
            return;
        }

        if (spec.getParameters().getDefinitions() == null || spec.getParameters().getDefinitions().size() == 0) {
            throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', no definitions found", spec.getId()));
        }

        if (spec.getParameters().getValueSets() == null || spec.getParameters().getValueSets().size() == 0) {
            throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', no value sets found", spec.getId()));
        }

        // Validate Parameter Definitions
        HashSet<String> parameterIds = new HashSet<>();
        for (ParameterDefinition definition : spec.getParameters().getDefinitions()) {
            if (definition.getId() == null) {
                throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: id of parameter may not be null", spec.getId()));
            }
            if (parameterIds.contains(definition.getId())) {
                throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: duplicate parameter id '%s'", spec.getId(), definition.getId()));
            }
            if (!PARAMETER_ID_PATTERN.matcher(definition.getId()).matches()) {
                throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: parameter id '%s' is invalid, id may only exists out of Latin letters, numbers, dash and underscore", spec.getId(), definition.getId()));
            }
            parameterIds.add(definition.getId());
            if (definition.getDisplayName() != null && StringUtils.isBlank(definition.getDisplayName())) {
                throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: displayName may not be blank of parameter with id '%s'", spec.getId(), definition.getId()));
            }
            if (definition.getDescription() != null && StringUtils.isBlank(definition.getDescription())) {
                throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: description may not be blank of parameter with id '%s'", spec.getId(), definition.getId()));
            }
        }

        // Check that either no defaults are provided or that all parameters has a default value
        List<String> defaults = spec.getParameters().getDefinitions().stream().map(ParameterDefinition::getDefaultValue).collect(Collectors.toList());
        if (!defaults.stream().allMatch(Objects::isNull) && !defaults.stream().allMatch(Objects::nonNull)) {
            throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: not every parameter has a default value. Either define no defaults, or defaults for all parameters.", spec.getId()));
        }

        // Validate Parameter Value Sets
        int valueSetIdx = 0;
        for (Parameters.ValueSet valueSet : spec.getParameters().getValueSets()) {
            for (String parameterId : spec.getParameters().getIds()) {
                if (!valueSet.containsParameter(parameterId)) {
                    throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: value set %s is missing values for parameter with id '%s'", spec.getId(), valueSetIdx, parameterId));
                }
                List<String> values = valueSet.getParameterValues(parameterId);
                Set<String> valuesAsSet = new HashSet<>(valueSet.getParameterValues(parameterId));
                if (values.size() != valuesAsSet.size()) {
                    throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: value set %s contains some duplicate values for parameter %s", spec.getId(), valueSetIdx, parameterId));
                }
            }
            if (valueSet.getParameterIds().size() != spec.getParameters().getIds().size()) {
                throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: value set %s contains values for more parameters than there are defined", spec.getId(), valueSetIdx));
            }
            valueSetIdx++;
        }

        // Check that every default value exists
        if (spec.getParameters().getDefinitions().get(0).getDefaultValue() != null) {
            for (ParameterDefinition definition : spec.getParameters().getDefinitions()) {
                boolean defaultValueExists = false;
                for (Parameters.ValueSet valueSet : spec.getParameters().getValueSets()) {
                    if (valueSet.getParameterValues(definition.getId()).contains(definition.getDefaultValue())) {
                        defaultValueExists = true;
                        break;
                    }
                }
                if (!defaultValueExists) {
                    throw new IllegalStateException(String.format("Configuration error: error in parameters of spec '%s', error: default value for parameter with id '%s' is not defined in a value-set", spec.getId(), definition.getId()));
                }
            }
        }

    }

    /**
     * Parses and validates the parameters provided by a user.
     * - checks that a value is included for every parameter
     * - checks that the user is allowed to use these values
     * - converts the (human friendly) name to the backend value
     *
     * @param auth               the user
     * @param spec               the Proxy spec to which this requests belong
     * @param providedParameters the parameters as provided by the user (using human friendly names)
     * @return the parsed parameters (using backend values)
     * @throws InvalidParametersException
     */
    public Optional<Pair<ParameterNames, ParameterValues>> parseAndValidateRequest(Authentication auth, ProxySpec spec, Map<String, String> providedParameters) throws InvalidParametersException {
        Parameters parameters = spec.getParameters();
        if (parameters == null) {
            return Optional.empty();
        }

        if (providedParameters == null) {
            throw new InvalidParametersException("No parameters provided, but proxy spec expects parameters");
        }

        // check if correct number of parameters is provided
        if (providedParameters.size() != parameters.getIds().size()) {
            throw new InvalidParametersException("Invalid number of parameters provided");
        }

        // check if all parameter ids are provided
        for (String parameterId : parameters.getIds()) {
            if (!providedParameters.containsKey(parameterId)) {
                throw new InvalidParametersException("Missing value for parameter " + parameterId);
            }
        }

        // check if the combination of values is allowed
        for (Parameters.ValueSet valueSet : parameters.getValueSets()) {
            if (!accessControlEvaluationService.checkAccess(auth, spec, valueSet.getAccessControl())) {
                continue;
            }
            Optional<Pair<ParameterNames, ParameterValues>> res = convertParametersIfAllowed(parameters.getDefinitions(), valueSet, providedParameters);
            if (res.isPresent()) {
                return res; // parameters are allowed, return the converted values
            }
        }

        throw new InvalidParametersException("Provided parameter values are not allowed");
    }

    /**
     * Checks whether the provided parameters are allowed by the given valueSet.
     * Returns the converted backend values if (and only if) the provided human-friendly values are allowed by this
     * valueSet.
     *
     * @param parameters         the parameter defintiions
     * @param valueSet           the valueSet to check
     * @param providedParameters the parameters as provided by the user (using human friendly names)
     * @return the converted values (i.e. using backend values) if allowed otherwise nothing
     */
    private Optional<Pair<ParameterNames, ParameterValues>> convertParametersIfAllowed(List<ParameterDefinition> parameters, Parameters.ValueSet valueSet, Map<String, String> providedParameters) {
        Map<String, String> backendValues = new HashMap<>();
        for (ParameterDefinition parameter : parameters) {
            if (!providedParameters.containsKey(parameter.getId())) {
                throw new IllegalStateException("Could not find value for parameter with id" + parameter.getId());
            }
            String providedValue = providedParameters.get(parameter.getId());
            String backendValue = parameter.getValueForName(providedValue);
            if (backendValue == null) {
                // if we did not find a backend value for the provided value (i.e. the user already provided a backend value),
                // check that no mapping exists for this backend value.
                // The backend value can only be used if a mapping does not exist.
                if (parameter.hasNameForValue(providedValue)) {
                    return Optional.empty();
                } else {
                    backendValue = providedValue;
                }
            }
            // check whether the backendValue is in the list of allowed values of this valueSet
            if (!valueSet.getParameterValues(parameter.getId()).contains(backendValue)) {
                return Optional.empty();
            }
            backendValues.put(parameter.getId(), backendValue);
        }
        // providedParameters contains an allowed value for every parameter
        ParameterNames parameterNames = new ParameterNames(getParameterNames(parameters, providedParameters));
        ParameterValues parameterValues = new ParameterValues(backendValues, valueSet.getName());
        return Optional.of(Pair.of(parameterNames, parameterValues));
    }

    /**
     * Creates ParamaterNames representation for the chosen parameters.
     * This is the public value which can be seen by the user (e.g. in API responses).
     *
     * @param parameters         parameter definitions
     * @param providedParameters values chosen by the user
     * @return
     */
    private List<ParameterNames.ParameterName> getParameterNames(List<ParameterDefinition> parameters, Map<String, String> providedParameters) {
        List<ParameterNames.ParameterName> res = new ArrayList<>();
        for (ParameterDefinition parameter : parameters) {
            res.add(new ParameterNames.ParameterName(parameter.getDisplayNameOrId(), parameter.getDescription(), providedParameters.get(parameter.getId())));
        }
        return res;
    }

    public AllowedParametersForUser calculateAllowedParametersForUser(Authentication auth, ProxySpec proxySpec, ParameterValues previousParameters) {
        Parameters parameters = proxySpec.getParameters();
        if (parameters == null) {
            return new AllowedParametersForUser(new HashMap<>(), new HashSet<>(), null);
        }
        List<String> parameterIds = parameters.getIds();

        // 1. check which ValueSets are allowed for this user
        List<Parameters.ValueSet> allowedValueSets = parameters.getValueSets().stream()
                .filter(v -> accessControlEvaluationService.checkAccess(auth, proxySpec, v.getAccessControl()))
                .collect(Collectors.toList());

        // 2. compute a unique (per parameter id) index for every value
        // mapping of parameter id to a mapping of an allowed value and its index
        Map<String, Map<String, Integer>> valuesToIndex = new HashMap<>();
        Map<String, List<String>> values = new HashMap<>();
        // for every set of allowed values
        for (Parameters.ValueSet valueSet : allowedValueSets) {
            // for every parameter in this set
            for (ParameterDefinition parameter : parameters.getDefinitions()) {
                valuesToIndex.computeIfAbsent(parameter.getId(), (k) -> new HashMap<>());
                values.computeIfAbsent(parameter.getId(), (k) -> new ArrayList<>());
                // for every value of this parameter
                for (String value : valueSet.getParameterValues(parameter.getId())) {
                    if (!valuesToIndex.get(parameter.getId()).containsKey(value)) {
                        // add it to allValues if it does not yet exist
                        Integer newIndex = values.get(parameter.getId()).size() + 1;
                        valuesToIndex.get(parameter.getId()).put(value, newIndex);
                        values.get(parameter.getId()).add(parameter.getNameOfValue(value));
                    }
                }
            }
        }

        // 3. compute the set of allowed values for every value-set
        HashSet<List<Integer>> allowedCombinations = new HashSet<>();

        for (Parameters.ValueSet valueSet : allowedValueSets) {
            allowedCombinations.addAll(getAllowedCombinationsForSingleValueSet(parameterIds, valueSet, valuesToIndex));
        }

        // 4. compute default value
        List<Integer> defaultValue = getDefaultValue(parameters.getDefinitions(), allowedCombinations, valuesToIndex, previousParameters);

        return new AllowedParametersForUser(values, allowedCombinations, defaultValue);

    }

    private List<List<Integer>> getAllowedCombinationsForSingleValueSet(List<String> parameterIds,
                                                                        Parameters.ValueSet valueSet,
                                                                        Map<String, Map<String, Integer>> valuesToIndex
    ) {
        // start with an empty combination
        List<List<Integer>> newAllowedCombinations = new ArrayList<>();
        newAllowedCombinations.add(new ArrayList<>());

        // for each parameter
        for (String parameterId : parameterIds) {
            // copy the combinations calculated during the previous iteration
            List<List<Integer>> previousAllowedCombinations = new ArrayList<>(newAllowedCombinations);
            newAllowedCombinations = new ArrayList<>();
            // for every allowed value of this parameter
            for (String allowedValue : valueSet.getParameterValues(parameterId)) {
                // and for every combination of the previous iteration
                for (List<Integer> combination : previousAllowedCombinations) {
                    // create a new combination with the additional value
                    combination = new ArrayList<>(combination); // copy the previous combination so that we can extend it
                    Integer index = valuesToIndex.get(parameterId).get(allowedValue);
                    combination.add(index);
                    newAllowedCombinations.add(combination);
                }
            }
        }
        return newAllowedCombinations;
    }

    private List<Integer> getDefaultValue(List<ParameterDefinition> definitions, HashSet<List<Integer>> allowedCombinations, Map<String, Map<String, Integer>> valuesToIndex, ParameterValues previousParameters) {
        List<Integer> noDefault = new ArrayList<>(Collections.nCopies(definitions.size(), 0));
        List<Integer> result = new ArrayList<>();

        List<Integer> previouslyUsedParameters = getPreviouslyUsedParameters(definitions, allowedCombinations, valuesToIndex, previousParameters);
        if (previouslyUsedParameters != null) {
            return previouslyUsedParameters;
        }

        if (definitions.get(0).getDefaultValue() == null) {
            return noDefault; // no default values defined
        }
        for (ParameterDefinition definition : definitions) {
            Integer valueIndex = valuesToIndex.get(definition.getId()).get(definition.getDefaultValue());
            if (valueIndex == null) {
                return noDefault; // default value cannot be used by this user
            }
            result.add(valueIndex);
        }
        if (allowedCombinations.contains(result)) {
            return result;
        }
        return noDefault; // this combination cannot be used by the user
    }

    private List<Integer> getPreviouslyUsedParameters(List<ParameterDefinition> definitions, HashSet<List<Integer>> allowedCombinations, Map<String, Map<String, Integer>> valuesToIndex, ParameterValues previousParameters) {
        if (previousParameters == null || previousParameters.getBackendValues() == null) {
            return null;
        }

        List<Integer> result = new ArrayList<>();
        for (ParameterDefinition definition : definitions) {
            Integer valueIndex = valuesToIndex.get(definition.getId()).get(previousParameters.getBackendValues().get(definition.getId()));
            if (valueIndex == null) {
                return null; // default value cannot be used by this user
            }
            result.add(valueIndex);
        }

        if (allowedCombinations.contains(result)) {
            return result;
        }

        return null;
    }

}
