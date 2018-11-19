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

import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.kerberos.authentication.KerberosTicketValidation;
import org.springframework.security.kerberos.authentication.KerberosTicketValidator;

/**
 * Based on SunJaasKerberosTicketValidator but modified with isInitiator=true so that
 * it stores the service credentials in the Subject for later reuse.
 */
public class KRBTicketValidator implements KerberosTicketValidator {

	private String servicePrincipal;
	private Resource keyTabLocation;
	private Subject serviceSubject;
	private boolean debug = false;

	public KRBTicketValidator(String servicePrincipal, Resource keyTabLocation) {
		this.servicePrincipal = servicePrincipal;
		this.keyTabLocation = keyTabLocation;
		this.debug = true;
	}

	public void init() throws Exception {
		String keyTabLocationAsString = this.keyTabLocation.getURL().toExternalForm();
		if (keyTabLocationAsString.startsWith("file:")) {
			keyTabLocationAsString = keyTabLocationAsString.substring(5);
		}
		LoginConfig loginConfig = new LoginConfig(keyTabLocationAsString, this.servicePrincipal, this.debug);
		Set<Principal> princ = new HashSet<Principal>(1);
		princ.add(new KerberosPrincipal(this.servicePrincipal));
		Subject sub = new Subject(false, princ, new HashSet<Object>(), new HashSet<Object>());
		LoginContext lc = new LoginContext("", sub, null, loginConfig);
		lc.login();
		this.serviceSubject = lc.getSubject();
	}

	@Override
	public KerberosTicketValidation validateTicket(byte[] token) {
		try {
			return Subject.doAs(this.serviceSubject, new KerberosValidateAction(token));
		}
		catch (PrivilegedActionException e) {
			throw new BadCredentialsException("Kerberos validation not successful", e);
		}
	}

	private class KerberosValidateAction implements PrivilegedExceptionAction<KerberosTicketValidation> {
		byte[] kerberosTicket;

		public KerberosValidateAction(byte[] kerberosTicket) {
			this.kerberosTicket = kerberosTicket;
		}

		@Override
		public KerberosTicketValidation run() throws Exception {
			byte[] responseToken = new byte[0];
			GSSName gssName = null;
			GSSContext context = GSSManager.getInstance().createContext((GSSCredential) null);
			while (!context.isEstablished()) {
				responseToken = context.acceptSecContext(kerberosTicket, 0, kerberosTicket.length);
				gssName = context.getSrcName();
				if (gssName == null) {
					throw new BadCredentialsException("GSSContext name of the context initiator is null");
				}
			}
			context.dispose();
			return new KerberosTicketValidation(gssName.toString(), servicePrincipal, responseToken, context);
		}
	}
	
	private static class LoginConfig extends Configuration {
        private String keyTabLocation;
        private String servicePrincipalName;
        private boolean debug;

        public LoginConfig(String keyTabLocation, String servicePrincipalName, boolean debug) {
            this.keyTabLocation = keyTabLocation;
            this.servicePrincipalName = servicePrincipalName;
            this.debug = debug;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            HashMap<String, String> options = new HashMap<String, String>();
            options.put("useKeyTab", "true");
            options.put("keyTab", this.keyTabLocation);
            options.put("principal", this.servicePrincipalName);
            options.put("storeKey", "true");
            options.put("doNotPrompt", "true");
            if (this.debug) {
                options.put("debug", "true");
            }
            options.put("isInitiator", "true");

            return new AppConfigurationEntry[] { new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options), };
        }

    }
}