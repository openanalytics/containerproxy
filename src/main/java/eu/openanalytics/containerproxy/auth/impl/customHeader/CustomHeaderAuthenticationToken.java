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
package eu.openanalytics.containerproxy.auth.impl.customHeader;

import org.springframework.security.authentication.AbstractAuthenticationToken;

public class CustomHeaderAuthenticationToken extends AbstractAuthenticationToken{

    private final String remoteUser;

    public CustomHeaderAuthenticationToken(String userName) {
        super(null);
        this.remoteUser = userName;
    }

    public boolean isValid() { 
        if (remoteUser != null && !remoteUser.isEmpty()) 
            return true;
        return false;
    }


    @Override
    public Object getPrincipal() {
        return remoteUser;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
	public String getName() {
        return this.remoteUser;
    }

}

