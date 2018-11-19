package eu.openanalytics.containerproxy.auth.impl.kerberos;

import javax.security.auth.Subject;

import org.apache.kerby.kerberos.kerb.type.ticket.SgtTicket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KRBTicketRefreshJob implements Runnable {

	private Logger log = LogManager.getLogger(KRBTicketRefreshJob.class);
	
	private Subject serviceSubject;
	private String[] backendPrincipals;
	private KRBClientCacheRegistry ccacheReg;

	public KRBTicketRefreshJob(Subject serviceSubject, String[] backendPrincipals, KRBClientCacheRegistry ccacheReg) {
		this.serviceSubject = serviceSubject;
		this.backendPrincipals = backendPrincipals;
		this.ccacheReg = ccacheReg;
	}
	
	@Override
	public void run() {
		if (backendPrincipals == null || backendPrincipals.length == 0) return;
		
		try {
			for (String principal: ccacheReg.getAllPrincipals()) {
				String ccachePath = ccacheReg.get(principal);
				
				SgtTicket proxyTicket = KRBUtils.obtainImpersonationTicket(principal, serviceSubject);
				KRBUtils.persistTicket(proxyTicket, ccachePath);
				
				for (String backendPrincipal: backendPrincipals) {
					SgtTicket backendTicket = KRBUtils.obtainBackendServiceTicket(backendPrincipal, proxyTicket.getTicket(), serviceSubject);
					KRBUtils.persistTicket(backendTicket, ccachePath);
				}
				
				log.info("Renewed " + backendPrincipals.length + " service tickets for user " + principal);
			}
		} catch (Exception e) {
			log.error("Error in " + this.getClass().getSimpleName(), e);
		}
	}

}
