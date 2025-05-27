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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpHeaders {

    private final HeaderMap undertowHeaderMap = new HeaderMap();
    private final Map<String, String> headers;

    private final static Logger logger = LoggerFactory.getLogger(HttpHeaders.class);

    @JsonCreator
    public HttpHeaders(Map<String, String> headers) {
        Map<String, String> filteredHeaders = new HashMap<>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (!StandardCharsets.ISO_8859_1.newEncoder().canEncode(header.getValue())) {
                logger.warn("Header '{}' with value '{}', contains non ISO-8859-1/ISO-Latin-1 characters, not adding header.", header.getKey(), header.getValue());
                continue;
            }
            undertowHeaderMap.put(new HttpString(header.getKey()), header.getValue());
            filteredHeaders.put(header.getKey(), header.getValue());
        }
        this.headers = filteredHeaders;
    }

    public HeaderMap getUndertowHeaderMap() {
        return undertowHeaderMap;
    }

    @JsonValue
    public Map<String, String> jsonValue() {
        return headers;
    }

}
