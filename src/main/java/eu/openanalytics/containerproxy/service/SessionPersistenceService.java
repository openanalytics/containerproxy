package eu.openanalytics.containerproxy.service;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

@Service
public class SessionPersistenceService {

	protected static final String PROPERTY_PERSISTENCE_SESSIONS = "proxy.persistence_sessions";

	
	private Logger log = LogManager.getLogger(UserService.class);

	@Inject
	private Environment environment;
	
	@Inject
	private IContainerBackend containerBackend;
	
	@Inject
	private IProxySpecProvider proxySpecProvider;
	
	@Inject
	private ProxyService proxyService;
	
	@PostConstruct
	public void resumePreviousSessions() throws Exception {
		if (Boolean.valueOf(environment.getProperty("proxy.persistence_sessions", "false"))) {
			log.info("Peristence sessions enabled");

			Map<String, Proxy> proxies = new HashMap();

			for (ExistingContaienrInfo containerInfo: containerBackend.scanExistingContainers()) {				
				if (!proxies.containsKey(containerInfo.getProxyId())) {
					ProxySpec proxySpec = proxySpecProvider.getSpec(containerInfo.getProxySpecId());
					Proxy proxy = new Proxy();
					proxy.setId(containerInfo.getProxyId());
					proxy.setSpec(proxySpec);
					proxy.setStatus(ProxyStatus.Up); // TODO
//					proxy.setStartupTimestamp(); // TODO
					proxy.setUserId(containerInfo.getUserId()); // TODO
//					proxy.setTargets(); // TODO
					proxies.put(containerInfo.getProxyId(), proxy);
				} 
				Proxy proxy = proxies.get(containerInfo.getProxyId());
				Container container = new Container();
				container.setId(containerInfo.getContainerId());
//				container.setParameters(parameters); TODO
				container.setSpec(proxy.getSpec().getContainerSpec(containerInfo.getImage()));
				proxy.addContainer(container);
				
				log.info("Found container with container id: " + containerInfo.getContainerId() + ", proxyId: " + containerInfo.getProxyId() + ", specId: " + containerInfo.getProxySpecId());
			}
			
			for (Proxy proxy: proxies.values()) {
				proxyService.addExistingProxy(proxy);
			}
		
		} else {
			log.info("Peristence sessions disabled");
		}
		
	}
	
}
