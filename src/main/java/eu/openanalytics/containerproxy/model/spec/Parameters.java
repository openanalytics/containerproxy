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
package eu.openanalytics.containerproxy.model.spec;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Parameters {

    private List<ParameterDefinition> definitions;
    private List<ValueSet> valueSets;
    private String template;

    public List<ParameterDefinition> getDefinitions() {
        return definitions;
    }

    public List<String> getIds() {
        return definitions.stream().map(ParameterDefinition::getId).collect(Collectors.toList());
    }

    public void setDefinitions(List<ParameterDefinition> definitions) {
        this.definitions = definitions;
    }

    public List<ValueSet> getValueSets() {
        return valueSets;
    }

    public void setValueSets(List<ValueSet> valueSets) {
        this.valueSets = valueSets;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public static class ValueSet {

        private AccessControl accessControl;
        private Map<String, List<String>> values;

        private String name = null;

        public void setValues(Map<String, List<String>> values) {
            this.values = values;
        }

        public AccessControl getAccessControl() {
            return accessControl;
        }

        public void setAccessControl(AccessControl accessControl) {
            this.accessControl = accessControl;
        }

        public boolean containsParameter(String parameterId) {
            return values.containsKey(parameterId) && !values.get(parameterId).isEmpty();
        }

        public List<String> getParameterValues(String parameterId) {
            return values.get(parameterId);
        }

        public Set<String> getParameterIds() {
            return values.keySet();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
