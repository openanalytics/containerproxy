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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.security.auth.kerberos.KerberosTicket;

import org.apache.kerby.kerberos.kerb.type.ticket.SgtTicket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KRBTicketRenewalManager {

	private Logger log = LogManager.getLogger(KRBTicketRenewalManager.class);
	
	private String servicePrincipal;
	private String serviceKeytab;
	
	private volatile KerberosTicket serviceTGT;
	
	private String[] backendPrincipals;
	private KRBClientCacheRegistry ccacheReg;
	
	private ScheduledExecutorService executor;
	private long renewInterval;
	private Map<String, ScheduledFuture<?>> renewalJobs;
	
	public KRBTicketRenewalManager(String servicePrincipal, String serviceKeytab, String[] backendPrincipals, KRBClientCacheRegistry ccacheReg, long renewInterval) {
		this.servicePrincipal = servicePrincipal;
		this.serviceKeytab = serviceKeytab;
		this.backendPrincipals = backendPrincipals;
		
		this.ccacheReg = ccacheReg;
		
		this.executor = Executors.newSingleThreadScheduledExecutor();
		this.renewInterval = renewInterval;
		this.renewalJobs = new ConcurrentHashMap<>();
		
		executor.scheduleAtFixedRate(new ProxyRenewalJob(), 0, renewInterval, TimeUnit.MILLISECONDS);
	}
	
	public synchronized void start(String principal) {
		if (renewalJobs.containsKey(principal)) return;
		ScheduledFuture<?> f = executor.scheduleAtFixedRate(new RenewalJob(principal), 0, renewInterval, TimeUnit.MILLISECONDS);
		renewalJobs.put(principal, f);
	}
	
	public synchronized void stop(String principal) {
		ScheduledFuture<?> f = renewalJobs.get(principal);
		if (f != null) {
			f.cancel(true);
			renewalJobs.remove(principal);
		}
		try {
			ccacheReg.remove(principal);
		} catch (IOException e) {
			log.error("Error while removing ccache for " + principal, e);
		}
	}
	
	private class RenewalJob implements Runnable {

		private String principal;
		
		public RenewalJob(String principal) {
			this.principal = principal;
		}
		
		@Override
		public void run() {
			if (backendPrincipals == null || backendPrincipals.length == 0) return;
			
			try {
				String ccachePath = ccacheReg.get(principal);
				if (ccachePath == null) ccachePath = ccacheReg.create(principal);
				
				SgtTicket proxyTicket = KRBUtils.obtainImpersonationTicket(principal, serviceTGT);
				KRBUtils.persistTicket(proxyTicket, ccachePath);
					
				for (String backendPrincipal: backendPrincipals) {
					SgtTicket backendTicket = KRBUtils.obtainBackendServiceTicket(backendPrincipal, proxyTicket.getTicket(), serviceTGT);
					KRBUtils.persistTicket(backendTicket, ccachePath);
				}
					
				log.info("Renewed " + backendPrincipals.length + " service tickets for user " + principal);
			} catch (Exception e) {
				log.error("Error while renewing service tickets for " + principal, e);
			}
		}
	}
	
	private class ProxyRenewalJob implements Runnable {
		@Override
		public void run() {
			try {
				serviceTGT = KRBUtils.createGSSContext(servicePrincipal, serviceKeytab);
			} catch (Exception e) {
				log.error("Error while renewing TGT for " + servicePrincipal, e);
			}
		}
	}
}
