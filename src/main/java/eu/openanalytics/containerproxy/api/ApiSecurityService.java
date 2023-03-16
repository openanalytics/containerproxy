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
package eu.openanalytics.containerproxy.api;

import eu.openanalytics.containerproxy.model.Views;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;

@Service
public class ApiSecurityService {

    public static final String PROP_API_SECURITY_HIDE_SPEC_DETAILS = "proxy.api-security.hide-spec-details";

    private final boolean hideSpecDetails;

    public ApiSecurityService(Environment environment) {
        hideSpecDetails = environment.getProperty(PROP_API_SECURITY_HIDE_SPEC_DETAILS, Boolean.class, true);
    }

    public MappingJacksonValue protectSpecs(Object specs) {
        MappingJacksonValue value = new MappingJacksonValue(specs);

        if (hideSpecDetails) {
            value.setSerializationView(Views.UserApi.class);
        }

        return value;
    }

}
