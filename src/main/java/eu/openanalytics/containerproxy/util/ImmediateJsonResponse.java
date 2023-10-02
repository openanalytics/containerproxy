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

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ImmediateJsonResponse {

    /**
     * Helper that writes a JSON response to a HttpServletResponse without using Spring.
     * This can be used in places where no Spring logic can be used (e.g. Filters)
     * @param response the response to write into
     * @param status the status code of the response
     * @param json the jsons string to write
     * @throws IOException
     */
    public static void write(HttpServletResponse response, int status, String json) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(json);
        response.setStatus(status);
    }

}
