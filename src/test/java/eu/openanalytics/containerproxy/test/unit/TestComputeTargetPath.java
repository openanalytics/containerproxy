/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
import eu.openanalytics.containerproxy.test.proxy.PropertyOverrideContextInitializer;
import eu.openanalytics.containerproxy.test.proxy.TestProxyService.TestConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;


@SpringBootTest(classes= {TestConfiguration.class, ContainerProxyApplication.class})
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
public class TestComputeTargetPath {

	@Test
	public void testComputerTargetPath() {
	    Assertions.assertEquals("", AbstractContainerBackend.computeTargetPath(""));
		Assertions.assertEquals("", AbstractContainerBackend.computeTargetPath(null));
		Assertions.assertEquals("", AbstractContainerBackend.computeTargetPath("/"));
		Assertions.assertEquals("", AbstractContainerBackend.computeTargetPath("//"));
		Assertions.assertEquals("", AbstractContainerBackend.computeTargetPath("///"));
		Assertions.assertEquals("", AbstractContainerBackend.computeTargetPath("////"));
		Assertions.assertEquals("/test/abc/test", AbstractContainerBackend.computeTargetPath("//test//abc/test//"));
		Assertions.assertEquals("/test/abc/test", AbstractContainerBackend.computeTargetPath("test//abc/test//"));
		Assertions.assertEquals("/test/abc/test", AbstractContainerBackend.computeTargetPath("test//abc/test"));
	}
	
}
