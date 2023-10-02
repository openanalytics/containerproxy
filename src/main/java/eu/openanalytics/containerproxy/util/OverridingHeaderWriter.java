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

import org.springframework.security.web.header.Header;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class OverridingHeaderWriter implements HeaderWriter {

    private final List<Header> headers;

    public OverridingHeaderWriter(List<Header> headers) {
        Assert.notEmpty(headers, "headers cannot be null or empty");
        this.headers = headers;
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        for (Header header : this.headers) {
            response.setHeader(header.getName(), header.getValues().get(0));
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + " [headers=" + this.headers + "]";
    }

}
