/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.containerproxy.test.proxy;

import java.net.URI;

import javax.inject.Inject;

import eu.openanalytics.containerproxy.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.test.proxy.TestProxyService.TestConfiguration;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;

@SpringBootTest(classes= {TestConfiguration.class, ContainerProxyApplication.class})
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
public class TestProxyService {

	@Inject
	private Environment environment;
	
	@Inject
	private ProxyService proxyService;
	
	@Test
	public void launchProxy() {
		String specId = environment.getProperty("proxy.specs[0].id");

		ProxySpec baseSpec = proxyService.findProxySpec(s -> s.getId().equals(specId), true);
		ProxySpec spec = proxyService.resolveProxySpec(baseSpec, null, null);

		Proxy proxy = proxyService.startProxy(spec, true);
		proxyService.stopProxy(proxy, false, true);
	}
	
	public static class TestConfiguration {
		@Bean
		@Primary
		public ProxyMappingManager mappingManager() {
			return new NoopMappingManager();
		}

		@Bean
		@Primary
		public UserService mockedUserService() {
			return new MockedUserService();
		}
	}
	
	public static class NoopMappingManager extends ProxyMappingManager {
		@Override
		public synchronized void addMapping(String proxyId, String path, URI target) {
			// No-op
			System.out.println("NOOP");
		}
		
		@Override
		public synchronized void removeMapping(String path) {
			// No-ops
		}
	}

}
