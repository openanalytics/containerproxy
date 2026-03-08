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
package eu.openanalytics.containerproxy.auth.impl.spcs;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.Collection;

public class SpcsAuthenticationToken extends AbstractAuthenticationToken {

    private final String spcsIngressUserName;  // Sf-Context-Current-User header value
    private final String spcsIngressUserToken; // Sf-Context-Current-User-Token header value
    private final WebAuthenticationDetails details;

    public SpcsAuthenticationToken(String spcsIngressUserName, String spcsIngressUserToken, WebAuthenticationDetails details) {
        super(null);
        this.spcsIngressUserName = spcsIngressUserName;
        this.spcsIngressUserToken = spcsIngressUserToken;
        this.details = details;
        super.setAuthenticated(false);
    }

    public SpcsAuthenticationToken(String spcsIngressUserName, String spcsIngressUserToken, Collection<GrantedAuthority> authorities, WebAuthenticationDetails details, boolean isAuthenticated) {
        super(authorities);
        this.spcsIngressUserName = spcsIngressUserName;
        this.spcsIngressUserToken = spcsIngressUserToken;
        this.details = details;
        super.setAuthenticated(isAuthenticated);
    }
    
    public boolean isValid() {
        return spcsIngressUserName != null && !spcsIngressUserName.isBlank();
    }

    @Override
    public Object getPrincipal() {
        return spcsIngressUserName;
    }

    @Override
    public Object getCredentials() {
        // Return the SPCS ingress user token as the credential (proof of identity)
        // This follows Spring Security conventions where credentials represent authentication proof
        return spcsIngressUserToken;
    }

    @Override
    public String getName() {
        return this.spcsIngressUserName;
    }

    @Override
    public WebAuthenticationDetails getDetails() {
        return this.details;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {
        throw new SpcsAuthenticationException("Cannot change authenticated after initialization!");
    }

}
