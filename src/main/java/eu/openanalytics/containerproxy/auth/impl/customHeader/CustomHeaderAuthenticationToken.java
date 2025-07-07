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
package eu.openanalytics.containerproxy.auth.impl.customHeader;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class CustomHeaderAuthenticationToken extends AbstractAuthenticationToken {

    private final String username;

    public CustomHeaderAuthenticationToken(String username, Collection<GrantedAuthority> authorities, boolean isAuthenticated) {
        super(authorities);
        this.username = username;
        super.setAuthenticated(isAuthenticated);
    }

    public boolean isValid() {
        return username != null && !username.isBlank();
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return this.username;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {
        throw new CustomHeaderAuthenticationException("Cannot change authenticated after initialization!");
    }

}
