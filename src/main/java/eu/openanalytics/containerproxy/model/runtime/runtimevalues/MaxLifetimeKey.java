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

public class MaxLifetimeKey extends RuntimeValueKey<Long> {

    private MaxLifetimeKey() {
        super("openanalytics.eu/sp-max-lifetime",
                "SHINYPROXY_MAX_LIFETIME",
                false,
                true,
                false,
                true,
                true, Long.class);
    }

    public static MaxLifetimeKey inst = new MaxLifetimeKey();

    @Override
    public Long fromString(String value) {
        return Long.parseLong(value);
    }

}
