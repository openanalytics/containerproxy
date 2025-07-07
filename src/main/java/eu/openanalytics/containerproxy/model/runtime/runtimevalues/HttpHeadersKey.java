/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HttpHeadersKey extends RuntimeValueKey<HttpHeaders> {

    public static final HttpHeadersKey inst = new HttpHeadersKey();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpHeadersKey() {
        super("openanalytics.eu/sp-http-headers",
            "SHINYPROXY_HTTP_HEADERS",
            false,
            true,
            false,
            false, // IMPORTANT: may not be exposed through the API
            false,
            false,
            HttpHeaders.class);
    }

    @Override
    public HttpHeaders deserializeFromString(String value) {
        try {
            return objectMapper.readValue(value, HttpHeaders.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String serializeToString(HttpHeaders value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
