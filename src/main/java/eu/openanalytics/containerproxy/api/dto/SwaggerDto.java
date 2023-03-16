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
package eu.openanalytics.containerproxy.api.dto;

import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;

import java.util.List;

public class SwaggerDto {

    @JsonView(Views.UserApi.class)
    public static class ProxyResponse {
        public String status = "success";
        public Proxy data;
    }

    @JsonView(Views.UserApi.class)
    public static class ProxiesResponse {
        public String status = "success";
        public List<Proxy> data;
    }

    public static class ProxySpecsResponse {
        public String status = "success";
        public List<ProxySpec> data;
    }

    public static class ProxySpecResponse {
        public String status = "success";
        public ProxySpec data;
    }

}
