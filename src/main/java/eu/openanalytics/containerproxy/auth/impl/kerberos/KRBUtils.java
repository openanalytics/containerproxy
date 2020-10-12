/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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

import java.io.File;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;

import org.apache.kerby.kerberos.kerb.ccache.Credential;
import org.apache.kerby.kerberos.kerb.ccache.CredentialCache;
import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.client.KrbConfig;
import org.apache.kerby.kerberos.kerb.type.KerberosTime;
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
	public static KerberosTicket createGSSContext(String principal, String keytabPath) throws Exception {
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

		if (sun.security.krb5.internal.Krb5.DEBUG) {
			sun.security.krb5.Config config = sun.security.krb5.Config.getInstance();
			System.out.println("DEBUG: Config isForwardable = " + getBooleanValue(config, "libdefaults", "forwardable"));
			sun.security.krb5.internal.KDCOptions opts = new sun.security.krb5.internal.KDCOptions();
			System.out.println("DEBUG: KDCOptions isForwardable = " + opts.get(sun.security.krb5.internal.Krb5.TKT_OPTS_FORWARDABLE));
			System.out.println("DEBUG: Requesting TGT for " + principal);
		}

		Subject proxySubject = new Subject(false, princ, new HashSet<Object>(), new HashSet<Object>());
		LoginContext lc = new LoginContext("", proxySubject, null, cfg);
		lc.login();

		KerberosTicket tgt = findServiceTGT(proxySubject);

		if (sun.security.krb5.internal.Krb5.DEBUG) {
			System.out.println("DEBUG: TGT (KerberosTicket) isForwardable = " + tgt.isForwardable());
		}

		return tgt;
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

	/**
	 * Obtain a Service Ticket (SGT) from a KDC, using the S4U2Self extension.
	 * The resulting ticket is from the proxy service for the proxy service,
	 * on behalf of the client principal.
	 *
	 * @param clientPrincipal The client principal on whose behalf to request a ticket.
	 * @param serviceTGT The proxy TGT.
	 * @return A SGT from the proxy service, for the proxy service.
	 * @throws Exception
	 */
	@SuppressWarnings("restriction")
	public static SgtTicket obtainImpersonationTicket(String clientPrincipal, KerberosTicket serviceTGT) throws Exception {
		// Get our own TGT that will be used to make the S4U2Proxy request
		sun.security.krb5.Credentials serviceTGTCreds = sun.security.jgss.krb5.Krb5Util.ticketToCreds(serviceTGT);

		if (sun.security.krb5.internal.Krb5.DEBUG) {
			sun.security.krb5.Config config = sun.security.krb5.Config.getInstance();
			System.out.println("DEBUG: Config isForwardable = " + getBooleanValue(config, "libdefaults", "forwardable"));
			sun.security.krb5.internal.KDCOptions opts = new sun.security.krb5.internal.KDCOptions();
			System.out.println("DEBUG: KDCOptions isForwardable = " + opts.get(sun.security.krb5.internal.Krb5.TKT_OPTS_FORWARDABLE));
			System.out.println("DEBUG: TGT (KerberosTicket) isForwardable = " + serviceTGT.isForwardable());
			System.out.println("DEBUG: TGT (Credentials) isForwardable = " + serviceTGTCreds.isForwardable());
			System.out.println("DEBUG: Requesting impersonation ticket (S4U2self) for user " + clientPrincipal);
		}
		
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
	 * @param serviceTGT The proxy TGT
	 * @return A SGT for the specified backend service
	 * @throws Exception If the ticket cannot be obtained for any reason
	 */
	@SuppressWarnings("restriction")
	public static SgtTicket obtainBackendServiceTicket(String backendServiceName, Ticket proxyServiceTicket, KerberosTicket serviceTGT) throws Exception {
		// Get client's ST that was submitted inside the SPNEGO token
		sun.security.krb5.internal.Ticket sunTicket = new sun.security.krb5.internal.Ticket(proxyServiceTicket.encode());

		// Get our own TGT that will be used to make the S4U2Proxy request
		sun.security.krb5.Credentials serviceTGTCreds = sun.security.jgss.krb5.Krb5Util.ticketToCreds(serviceTGT);

		if (sun.security.krb5.internal.Krb5.DEBUG) {
			System.out.println("DEBUG: Requesting backend service ticket (S4U2proxy) for service " + backendServiceName);
		}
		
		// Make a S4U2Proxy request to get a backend ST
		sun.security.krb5.Credentials creds = sun.security.krb5.internal.CredentialsUtil.acquireS4U2proxyCreds(
				backendServiceName,
				sunTicket,
				serviceTGTCreds.getClient(),
				serviceTGTCreds);
		
		SgtTicket sgtTicket = convertToTicket(creds, backendServiceName, proxyServiceTicket.getRealm());
		return sgtTicket;
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
		File cCacheFile = new File(destinationCCache);
		if (cCacheFile.exists()) {
			CredentialCache cCache = new CredentialCache();
			cCache.load(cCacheFile);
			
			// Fix: kerby refuses to overwrite tickets in ccache, so if an older one exists, force removal now
			Credential newCred = new Credential(ticket, ticket.getClientPrincipal());
			Credential existingCred = cCache.getCredentials().stream()
					.filter(c -> c.getServerName().getName().equals(newCred.getServerName().getName()))
					.findAny().orElse(null);

			cCache.removeCredential(existingCred);
			cCache.addCredential(newCred);
			cCache.store(cCacheFile);
		} else {
			krbClient.storeTicket(ticket, cCacheFile);
		}
	}

	/**
	 * Used to provide compatibility between differnt JDKs.
	 * The Config.getBooleanValue is removed in newer versions in favor of getBooleanObject.
	 * However, getBooleanObject is private in older versions.
	 */
	private static boolean getBooleanValue(sun.security.krb5.Config config, String...keys) {
        String val = config.get(keys);
        if (val != null && val.equalsIgnoreCase("true")) {
            return true;
        } else {
            return false;
        }
		
	}
}
