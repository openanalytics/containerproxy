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

import java.lang.reflect.Field;

import javax.security.auth.Subject;

import org.apache.kerby.kerberos.kerb.type.ticket.SgtTicket;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.kerberos.authentication.KerberosServiceAuthenticationProvider;
import org.springframework.security.kerberos.authentication.KerberosServiceRequestToken;

public class KRBServiceAuthProvider extends KerberosServiceAuthenticationProvider {
	
	private String backendPrincipal;
	private KRBClientCacheRegistry ccacheReg;
	
	public KRBServiceAuthProvider(String backendPrincipal, KRBClientCacheRegistry ccacheReg) {
		this.backendPrincipal = backendPrincipal;
		this.ccacheReg = ccacheReg;
	}
	
	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		KerberosServiceRequestToken auth = (KerberosServiceRequestToken) super.authenticate(authentication);
		
		try {
			Subject subject = getCurrentSubject();
			String ccachePath = ccacheReg.create(auth.getName());

			// Test: use s4u2self instead of spnego ticket
			
//			SgtTicket proxyTicket = KRBUtils.obtainProxyServiceTicket(auth.getToken(), subject);
//			KRBUtils.persistTicket(proxyTicket, ccachePath);

			SgtTicket proxyTicket = KRBUtils.obtainImpersonationTicket(auth.getName(), subject);
			KRBUtils.persistTicket(proxyTicket, ccachePath);
			
			SgtTicket backendTicket = KRBUtils.obtainBackendServiceTicket(backendPrincipal, proxyTicket.getTicket(), subject);
			KRBUtils.persistTicket(backendTicket, ccachePath);
		} catch (Exception e) {
			throw new BadCredentialsException("Failed to create client ccache", e);
		}
	
		return auth;
	}
		
	/**
	 * Obtain the current subject (containing credential information) that was
	 * generated during the initialization of the KerberosTicketValidator.
	 */
	private Subject getCurrentSubject() throws Exception {
		Field ticketValidatorField = KerberosServiceAuthenticationProvider.class.getDeclaredField("ticketValidator");
		ticketValidatorField.setAccessible(true);
		Object ticketValidator = ticketValidatorField.get(this);
		
		Field subjectField = ticketValidator.getClass().getDeclaredField("serviceSubject");
		subjectField.setAccessible(true);
		Subject subject = (Subject) subjectField.get(ticketValidator);
		
		return subject;
	}
	
}