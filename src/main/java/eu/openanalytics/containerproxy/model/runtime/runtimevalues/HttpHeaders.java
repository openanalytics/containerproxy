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
package eu.openanalytics.containerproxy.model.runtime.runtimevalues;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

import java.util.Map;

public class HttpHeaders {

    private final HeaderMap undertowHeaderMap = new HeaderMap();
    private final Map<String, String> headers;

    @JsonCreator
    public HttpHeaders(Map<String, String> headers) {
        this.headers = headers;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            undertowHeaderMap.put(new HttpString(header.getKey()), header.getValue());
        }
    }

    public HeaderMap getUndertowHeaderMap() {
        return undertowHeaderMap;
    }

    @JsonValue
    public Map<String, String> jsonValue() {
        return headers;
    }

}
