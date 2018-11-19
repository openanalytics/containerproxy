/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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
package eu.openanalytics.containerproxy.auth.impl.kerberos;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.kerberos.authentication.KerberosServiceRequestToken;

public class KRBAuthenticationToken extends AbstractAuthenticationToken {

	private static final long serialVersionUID = 5316535191467106046L;
	
	private KerberosServiceRequestToken authenticatedToken;
	private String clientName;
	private String clientCCPath;
	
	public KRBAuthenticationToken(KerberosServiceRequestToken authenticatedToken) {
		super(null);
		this.authenticatedToken = authenticatedToken;
	}

	public String getClientName() {
		return clientName;
	}

	public void setClientName(String clientName) {
		this.clientName = clientName;
	}

	public String getClientCCPath() {
		return clientCCPath;
	}

	public void setClientCCPath(String clientCCPath) {
		this.clientCCPath = clientCCPath;
	}
	
	@Override
	public Object getCredentials() {
		return authenticatedToken.getCredentials();
	}
	
	@Override
	public Object getPrincipal() {
		return authenticatedToken.getPrincipal();
	}
	@Override
	public Collection<GrantedAuthority> getAuthorities() {
		return authenticatedToken.getAuthorities();
	}
	@Override
	public Object getDetails() {
		return authenticatedToken.getDetails();
	}
	@Override
	public String getName() {
		return authenticatedToken.getName();
	}
}
