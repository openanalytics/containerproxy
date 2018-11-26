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

import javax.security.auth.Subject;

import org.apache.kerby.kerberos.kerb.type.ticket.SgtTicket;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.kerberos.authentication.KerberosServiceAuthenticationProvider;
import org.springframework.security.kerberos.authentication.KerberosServiceRequestToken;

//TODO When user logs out: KRBClientCacheRegistry.remove(String principal)
//TODO Also renew the proxySubject
public class KRBServiceAuthProvider extends KerberosServiceAuthenticationProvider {

	private String[] backendPrincipals;
	private long ticketRenewInterval;
	
	private KRBTicketRenewalManager renewalManager;
	private KRBClientCacheRegistry ccacheReg;
	
	private String proxySvcPrincipal;
	private String proxySvcKeytab;
	private Subject proxySubject;
	
	public KRBServiceAuthProvider(String svcPrincipal, String svcKeytab, String[] backendPrincipals, KRBClientCacheRegistry ccacheReg, long ticketRenewInterval) {
		this.proxySvcPrincipal = svcPrincipal;
		this.proxySvcKeytab = svcKeytab;
		this.backendPrincipals = backendPrincipals;
		this.ccacheReg = ccacheReg;
		this.ticketRenewInterval = ticketRenewInterval;
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {
		proxySubject = KRBUtils.createGSSContext(proxySvcPrincipal, proxySvcKeytab);
		renewalManager = new KRBTicketRenewalManager(proxySubject, backendPrincipals, ccacheReg, ticketRenewInterval);
	}
	
	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		KerberosServiceRequestToken auth = (KerberosServiceRequestToken) super.authenticate(authentication);
		
		try {
			prepareUserCCache(auth.getName());
		} catch (Exception e) {
			throw new BadCredentialsException("Failed to create client ccache", e);
		}
	
		return auth;
	}
	
	private void prepareUserCCache(String userPrincipal) throws Exception {
		String ccachePath = ccacheReg.create(userPrincipal);

		SgtTicket proxyTicket = KRBUtils.obtainImpersonationTicket(userPrincipal, proxySubject);
		KRBUtils.persistTicket(proxyTicket, ccachePath);
		
		for (String backendPrincipal: backendPrincipals) {
			SgtTicket backendTicket = KRBUtils.obtainBackendServiceTicket(backendPrincipal, proxyTicket.getTicket(), proxySubject);
			KRBUtils.persistTicket(backendTicket, ccachePath);
		}
		
		renewalManager.start(userPrincipal);
	}
}