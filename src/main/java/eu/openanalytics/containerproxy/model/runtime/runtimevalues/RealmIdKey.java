/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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

public class RealmIdKey extends RuntimeValueKey<String> {

    public static final RealmIdKey inst = new RealmIdKey();

    private RealmIdKey() {
        super("openanalytics.eu/sp-realm-id",
            "SHINYPROXY_REALM_ID",
            false,
            true,
            true,
            false, // no need to expose in API
            false,
            false,
            String.class);
    }

    @Override
    public String deserializeFromString(String value) {
        return value;
    }

    @Override
    public String serializeToString(String value) {
        return value;
    }

}
