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

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class PropertyOverrideContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
				"proxy.kubernetes.namespace=" + TestIntegrationOnKube.namespace);

		PropertiesPropertySource defaultProperties = new PropertiesPropertySource("shinyProxyDefaultProperties", ContainerProxyApplication.getDefaultProperties());
		configurableApplicationContext.getEnvironment().getPropertySources().addFirst(defaultProperties);

	}
}
