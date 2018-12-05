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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.kerberos.KeyTab;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;

import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.client.KrbConfig;
import org.apache.kerby.kerberos.kerb.gss.impl.GssUtil;
import org.apache.kerby.kerberos.kerb.request.ApRequest;
import org.apache.kerby.kerberos.kerb.type.KerberosTime;
import org.apache.kerby.kerberos.kerb.type.ap.ApReq;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.apache.kerby.kerberos.kerb.type.kdc.EncTgsRepPart;
import org.apache.kerby.kerberos.kerb.type.ticket.SgtTicket;
import org.apache.kerby.kerberos.kerb.type.ticket.Ticket;
import org.apache.kerby.kerberos.kerb.type.ticket.TicketFlags;

public class KRBUtils {

	private static KrbClient krbClient = new KrbClient((KrbConfig) null);
	
	/**
	 * Perform a KRB5 login via GSS-API in order to obtain a TGT for the given principal.
	 * 
	 * @return A newly acquired TGT for the principal.
	 * @throws Exception If the login fails.
	 */
	@SuppressWarnings("restriction")
	public static Subject createGSSContext(String principal, String keytabPath) throws Exception {
		Configuration cfg = new Configuration() {
			@Override
			public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
				HashMap<String, String> options = new HashMap<String, String>();
				options.put("useKeyTab", "true");
				options.put("keyTab", keytabPath);
				options.put("principal", principal);
				options.put("storeKey", "true");
				options.put("doNotPrompt", "true");
				options.put("debug", "true");
				options.put("isInitiator", "true");
				return new AppConfigurationEntry[] {
					new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)
				};
			}

		};
		Set<Principal> princ = new HashSet<Principal>(1);
		princ.add(new KerberosPrincipal(principal));
		
		boolean debug = sun.security.krb5.internal.Krb5.DEBUG;
		
		if (debug) {
			sun.security.krb5.Config config = sun.security.krb5.Config.getInstance();
			boolean isForwardable = config.getBooleanValue("libdefaults", "forwardable");
			System.out.println("DEBUG: Config isForwardable = " + isForwardable);
			sun.security.krb5.internal.KDCOptions opts = new sun.security.krb5.internal.KDCOptions();
			isForwardable = opts.get(sun.security.krb5.internal.Krb5.TKT_OPTS_FORWARDABLE);
			System.out.println("DEBUG: KDCOptions isForwardable = " + isForwardable);
		}
		
		Subject proxySubject = new Subject(false, princ, new HashSet<Object>(), new HashSet<Object>());
		LoginContext lc = new LoginContext("", proxySubject, null, cfg);
		lc.login();
		
		if (debug) {
			KerberosTicket tgt = findServiceTGT(proxySubject);
			System.out.println("DEBUG: KerberosTicket TGT isForwardable = " + tgt.isForwardable());
			sun.security.krb5.Credentials tgtCreds = sun.security.jgss.krb5.Krb5Util.ticketToCreds(tgt);
			System.out.println("DEBUG: Credentials TGT isForwardable = " + tgtCreds.isForwardable());
		}
		
		return proxySubject;
	}
	
	/**
	 * Obtain the Service Ticket (SGT) that was included in an AP_REQ structure in a SPNEGO authentication.
	 * 
	 * @deprecated
	 * @param spNegoToken The raw SPNEGO token used for authentication 
	 * @param subject The Subject representing the proxy service (must include credentials)
	 * @return A Service Ticket from the client for the proxy service
	 * @throws Exception If the SPNEGO token cannot be parsed or validated
	 */
	public static SgtTicket obtainProxyServiceTicket(byte[] spNegoToken, Subject subject) throws Exception {
		ApReq req = parseApReq(spNegoToken);
		Ticket ticket = req.getTicket();
		int encryptType = ticket.getEncryptedEncPart().getEType().getValue();

		EncryptionKey sessionKey = findEncryptionKey(subject, encryptType);
		ApRequest.validate(sessionKey, req, null, 5 * 60 * 1000);
		
		EncTgsRepPart repPart = new EncTgsRepPart();
		repPart.setSname(ticket.getSname());
		repPart.setSrealm(ticket.getRealm());
		repPart.setAuthTime(ticket.getEncPart().getAuthTime());
		repPart.setStartTime(ticket.getEncPart().getStartTime());
		repPart.setEndTime(ticket.getEncPart().getEndTime());
		repPart.setRenewTill(ticket.getEncPart().getRenewtill());
		repPart.setFlags(ticket.getEncPart().getFlags());
		repPart.setKey(sessionKey);
		
		PrincipalName clientPrincipal = new PrincipalName(ticket.getEncPart().getCname().getName());
		clientPrincipal.setRealm(ticket.getEncPart().getCrealm());
		
		SgtTicket sgtTicket = new SgtTicket(ticket, repPart);
		sgtTicket.setClientPrincipal(clientPrincipal);
		return sgtTicket;
	}
	
	/**
	 * Parse the AP_REQ structure from a SPNEGO token
	 * 
	 * @deprecated
	 * @param spnegoToken A raw SPNEGO header to parse
	 * @return The parsed ApReq structure
	 * @throws IOException If the ApReq structure cannot be decoded
	 */
	private static ApReq parseApReq(byte[] spnegoToken) throws IOException {
		byte[] apReqHeader = {(byte) 0x1, (byte) 0};
		
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
	
	private static EncryptionKey findEncryptionKey(Subject subject, int encryptType) throws PrivilegedActionException {
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
	
	/**
	 * Obtain a Service Ticket (SGT) from a KDC, using the S4U2Self extension.
	 * The resulting ticket is from the proxy service for the proxy service,
	 * on behalf of the client principal.
	 * 
	 * @param clientPrincipal The client principal on whose behalf to request a ticket.
	 * @param subject The proxy subject, containing the proxy's own credentials
	 * @return A SGT from the proxy service, for the proxy service.
	 * @throws Exception 
	 */
	@SuppressWarnings("restriction")
	public static SgtTicket obtainImpersonationTicket(String clientPrincipal, Subject subject) throws Exception {
		// Get our own TGT that will be used to make the S4U2Proxy request
		KerberosTicket serviceTGT = findServiceTGT(subject);
		sun.security.krb5.Credentials serviceTGTCreds = sun.security.jgss.krb5.Krb5Util.ticketToCreds(serviceTGT);
		
		// Make a S4U2Self request
		sun.security.krb5.PrincipalName clientPName = new sun.security.krb5.PrincipalName(clientPrincipal);
		sun.security.krb5.Credentials creds = sun.security.krb5.Credentials.acquireS4U2selfCreds(clientPName, serviceTGTCreds);
		
		SgtTicket sgtTicket = convertToTicket(creds,
				serviceTGTCreds.getClient().getName(),
				serviceTGTCreds.getClient().getRealmAsString());
		return sgtTicket;
	}
	
	/**
	 * Request a Service Ticket (SGT) from a KDC, using the S4U2Proxy extension.
	 * This means that instead of a client's TGT, the service's own credentials are used
	 * in combination with a SGT from the client to the service.
	 * 
	 * @param backendServiceName The principal name of the backend service to request an SGT for
	 * @param proxyServiceTicket The ticket from the client for the proxy service
	 * @param subject The proxy subject, containing the proxy's own credentials
	 * @return A SGT for the specified backend service
	 * @throws Exception If the ticket cannot be obtained for any reason
	 */
	@SuppressWarnings("restriction")
	public static SgtTicket obtainBackendServiceTicket(String backendServiceName, Ticket proxyServiceTicket, Subject subject) throws Exception {
		// Get client's ST that was submitted inside the SPNEGO token
		sun.security.krb5.internal.Ticket sunTicket = new sun.security.krb5.internal.Ticket(proxyServiceTicket.encode());

		// Get our own TGT that will be used to make the S4U2Proxy request
		KerberosTicket serviceTGT = findServiceTGT(subject);
		sun.security.krb5.Credentials serviceTGTCreds = sun.security.jgss.krb5.Krb5Util.ticketToCreds(serviceTGT);

		// Make a S4U2Proxy request to get a backend ST
		sun.security.krb5.KrbTgsReq req = new sun.security.krb5.KrbTgsReq(
				serviceTGTCreds,
				sunTicket,
				new sun.security.krb5.PrincipalName(backendServiceName));
		sun.security.krb5.Credentials creds = req.sendAndGetCreds();

		SgtTicket sgtTicket = convertToTicket(creds, backendServiceName, proxyServiceTicket.getRealm());
		return sgtTicket;
	}
	
	private static KerberosTicket findServiceTGT(Subject subject) throws PrivilegedActionException {
		return Subject.doAs(subject, new PrivilegedExceptionAction<KerberosTicket>() {
			@Override
			public KerberosTicket run() throws Exception {
				for (Object cred: subject.getPrivateCredentials()) {
					if (cred instanceof KerberosTicket) {
						return (KerberosTicket) cred;
					}
				}
				return null;
			}
		});
	}
	
	@SuppressWarnings("restriction")
	private static SgtTicket convertToTicket(sun.security.krb5.Credentials creds, String sName, String sRealm) throws Exception {
		EncTgsRepPart rep = new EncTgsRepPart();
		rep.setSname(new PrincipalName(sName));
		rep.setSrealm(sRealm);
		rep.setAuthTime(new KerberosTime(creds.getAuthTime().getTime()));
		rep.setStartTime(new KerberosTime(creds.getStartTime().getTime()));
		rep.setEndTime(new KerberosTime(creds.getEndTime().getTime()));
		rep.setStartTime(new KerberosTime(creds.getAuthTime().getTime()));
		if (creds.getRenewTill() != null) rep.setRenewTill(new KerberosTime(creds.getRenewTill().getTime()));

		int flags = 0;
		boolean[] flagArray = creds.getFlags();
		for (int i = 0; i < flagArray.length; ++i) {
			flags = (flags << 1) + (flagArray[i] ? 1 : 0);
		}
		rep.setFlags(new TicketFlags(flags));
		
		EncryptionKey sessionKey = new EncryptionKey();
		sessionKey.decode(creds.getSessionKey().asn1Encode());
		rep.setKey(sessionKey);
		
		Ticket serviceTicket = new Ticket();
		serviceTicket.decode(creds.getEncoded());
		
		PrincipalName clientPrincipal = new PrincipalName(creds.getClient().getName());
		clientPrincipal.setRealm(creds.getClient().getRealmAsString());
		
		SgtTicket sgtTicket = new SgtTicket(serviceTicket, rep);
		sgtTicket.setClientPrincipal(clientPrincipal);
		
		return sgtTicket;
	}
	
	public static void persistTicket(SgtTicket ticket, String destinationCCache) throws Exception {
		File ccache = new File(destinationCCache);
		krbClient.storeTicket(ticket, ccache);
	}
}
