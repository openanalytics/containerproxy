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
package eu.openanalytics.containerproxy.model.runtime.runtimevalues;

import eu.openanalytics.containerproxy.model.runtime.ParameterValues;

public class ParameterValuesKey extends RuntimeValueKey<ParameterValues> {

    public ParameterValuesKey() {
        super("openanalytics.eu/sp-parameters",
                "SHINYPROXY_PARAMETERS",
                false,
                false, // TODO
                false,
                false, // IMPORTANT: the actual values may not be exposed through the API
                false, ParameterValues.class);
    }

    public static ParameterValuesKey inst = new ParameterValuesKey();


}