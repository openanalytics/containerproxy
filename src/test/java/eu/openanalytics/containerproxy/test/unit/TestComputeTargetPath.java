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
package eu.openanalytics.containerproxy.test.unit;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.backend.AbstractContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.test.proxy.MockedUserService;
import eu.openanalytics.containerproxy.test.proxy.TestProxyService.TestConfiguration;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.inject.Inject;
import java.net.URI;

import static org.junit.Assert.assertEquals;

@SpringBootTest(classes= {TestConfiguration.class, ContainerProxyApplication.class})
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
public class TestComputeTargetPath {

	@Test
	public void testComputerTargetPath() {
	    assertEquals("", AbstractContainerBackend.computeTargetPath(""));
		assertEquals("", AbstractContainerBackend.computeTargetPath(null));
		assertEquals("", AbstractContainerBackend.computeTargetPath("/"));
		assertEquals("", AbstractContainerBackend.computeTargetPath("//"));
		assertEquals("", AbstractContainerBackend.computeTargetPath("///"));
		assertEquals("", AbstractContainerBackend.computeTargetPath("////"));
		assertEquals("/test/abc/test", AbstractContainerBackend.computeTargetPath("//test//abc/test//"));
		assertEquals("/test/abc/test", AbstractContainerBackend.computeTargetPath("test//abc/test//"));
		assertEquals("/test/abc/test", AbstractContainerBackend.computeTargetPath("test//abc/test"));
	}
	
}
