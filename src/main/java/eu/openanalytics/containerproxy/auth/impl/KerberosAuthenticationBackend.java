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
package eu.openanalytics.containerproxy.auth.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KeyTab;

import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.client.KrbConfig;
import org.apache.kerby.kerberos.kerb.gss.impl.GssUtil;
import org.apache.kerby.kerberos.kerb.request.ApRequest;
import org.apache.kerby.kerberos.kerb.type.ap.ApReq;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey;
import org.apache.kerby.kerberos.kerb.type.kdc.EncAsRepPart;
import org.apache.kerby.kerberos.kerb.type.ticket.TgtTicket;
import org.apache.kerby.kerberos.kerb.type.ticket.Ticket;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.kerberos.authentication.KerberosAuthenticationProvider;
import org.springframework.security.kerberos.authentication.KerberosServiceAuthenticationProvider;
import org.springframework.security.kerberos.authentication.KerberosServiceRequestToken;
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosClient;
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosTicketValidator;
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter;
import org.springframework.security.kerberos.web.authentication.SpnegoEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;

public class KerberosAuthenticationBackend implements IAuthenticationBackend {

	public static final String NAME = "kerberos";

	@Inject
	Environment environment;
	
	@Inject
	AuthenticationManager authenticationManager;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public boolean hasAuthorization() {
		return true;
	}

	@Override
	public void configureHttpSecurity(HttpSecurity http) throws Exception {

		SpnegoAuthenticationProcessingFilter filter = new SpnegoAuthenticationProcessingFilter();
		filter.setAuthenticationManager(authenticationManager);

		http
			.exceptionHandling().authenticationEntryPoint(new SpnegoEntryPoint("/login")).and()
			.addFilterBefore(filter, BasicAuthenticationFilter.class);
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		UserDetailsService uds = new DummyUserDetailsService();

		KerberosAuthenticationProvider formAuthProvider = new KerberosAuthenticationProvider();
		SunJaasKerberosClient client = new SunJaasKerberosClient();
		client.setDebug(true);
		formAuthProvider.setKerberosClient(client);
		formAuthProvider.setUserDetailsService(uds);
		auth.authenticationProvider(formAuthProvider);

		KerberosServiceAuthenticationProvider spnegoAuthProvider = new CustomKerberosServiceAuthenticationProvider();
		SunJaasKerberosTicketValidator ticketValidator = new SunJaasKerberosTicketValidator();
		ticketValidator.setServicePrincipal(environment.getProperty("proxy.kerberos.service-principal"));
		ticketValidator.setKeyTabLocation(new FileSystemResource(environment.getProperty("proxy.kerberos.service-keytab")));
		ticketValidator.setDebug(true);
		ticketValidator.setHoldOnToGSSContext(true);
		ticketValidator.afterPropertiesSet();
		spnegoAuthProvider.setTicketValidator(ticketValidator);
		spnegoAuthProvider.setUserDetailsService(uds);
		auth.authenticationProvider(spnegoAuthProvider);
	}

	private static class DummyUserDetailsService implements UserDetailsService {
		@Override
		public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
			return new User(username, "", Collections.emptyList());
		}
	}

	private static class CustomKerberosServiceAuthenticationProvider extends KerberosServiceAuthenticationProvider {
		
		@Override
		public Authentication authenticate(Authentication authentication) throws AuthenticationException {
			KerberosServiceRequestToken auth = (KerberosServiceRequestToken) super.authenticate(authentication);
			
			try {
				ApReq apReq = parseApReq(auth.getToken());
				Ticket ticket = apReq.getTicket();
				
				Subject subject = getCurrentSubject();
				int encryptType = ticket.getEncryptedEncPart().getEType().getValue();
				EncryptionKey serviceKey = findEncryptionKey(subject, encryptType);
				
				ApRequest.validate(serviceKey, apReq, null, 5 * 60 * 1000);
				
				EncAsRepPart repInfo = new EncAsRepPart();
				repInfo.setSname(ticket.getSname());
				repInfo.setSrealm(ticket.getRealm());
				repInfo.setKey(serviceKey);
				repInfo.setAuthTime(ticket.getEncPart().getAuthTime());
				repInfo.setStartTime(ticket.getEncPart().getStartTime());
				repInfo.setEndTime(ticket.getEncPart().getEndTime());
				repInfo.setRenewTill(ticket.getEncPart().getRenewtill());
				repInfo.setFlags(ticket.getEncPart().getFlags());
				TgtTicket tgtTicket = new TgtTicket(ticket, repInfo, ticket.getEncPart().getCname());

				KrbClient krbClient = new KrbClient((KrbConfig) null);
				krbClient.storeTicket(tgtTicket, new File("/tmp/testcache"));
			} catch (Exception e) {
				throw new BadCredentialsException("Failed to persist client ticket to ccache", e);
			}
		
			return auth;
		}
		
		/**
		 * Parse the AP_REQ structure from the SPNEGO token
		 */
		private ApReq parseApReq(byte[] spnegoToken) throws IOException {
			byte[] apReqHeader = {(byte) 0x1, (byte) 0};
			
			//TODO Always 86 bytes?
			int offset = 0;
			while (offset < spnegoToken.length - 1) {
				if (spnegoToken[offset] == apReqHeader[0] && spnegoToken[offset + 1] == apReqHeader[1]) {
					offset += 2;
					break;
				}
				offset++;
			}
			
			ByteArrayOutputStream tokenMinusHeader = new ByteArrayOutputStream();
			tokenMinusHeader.write(spnegoToken, offset, spnegoToken.length - offset);
			
			ApReq apReq = new ApReq();
			apReq.decode(tokenMinusHeader.toByteArray());
			return apReq;
		}

		/**
		 * Obtain the current subject (containing credential information) that was
		 * generated during the initialization of the SunJaasKerberosTicketValidator
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
		
		private EncryptionKey findEncryptionKey(Subject subject, int encryptType) throws PrivilegedActionException {
			return Subject.doAs(subject, new PrivilegedExceptionAction<EncryptionKey>() {
				@Override
				public EncryptionKey run() throws Exception {
					Set<KerberosKey> keySet = new HashSet<>();
					for (Object cred: subject.getPrivateCredentials()) {
						if (cred instanceof KerberosKey) {
							keySet.add((KerberosKey) cred);
						} else if (cred instanceof KeyTab) {
							KeyTab kt = (KeyTab) cred;
							KerberosKey[] k = kt.getKeys(kt.getPrincipal());
							for (int i = 0; i < k.length; i++) keySet.add(k[i]);
						}
					}
					KerberosKey[] keys = keySet.toArray(new KerberosKey[0]);
					return GssUtil.getEncryptionKey(keys, encryptType);    		
				}
			});
		}
	}
}
