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
package eu.openanalytics.containerproxy.stat;

import eu.openanalytics.containerproxy.stat.impl.InfluxDBCollector;
import eu.openanalytics.containerproxy.stat.impl.JDBCCollector;
import eu.openanalytics.containerproxy.stat.impl.Micrometer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.inject.Inject;

@Configuration
class StatCollectorFactory {
	
	private final Logger log = LogManager.getLogger(StatCollectorFactory.class);
	
	@Inject
	private Environment environment;

	@Inject
	private ApplicationContext applicationContext;

	@Bean
	public IStatCollector statsCollector() {
		String baseURL = environment.getProperty("proxy.usage-stats-url");
		if (baseURL == null || baseURL.isEmpty()) {
			log.info("Disabled. Usage statistics will not be processed.");
			return null;
		}

		log.info(String.format("Enabled. Sending usage statistics to %s.", baseURL));

		if (baseURL.toLowerCase().contains("/write?db=")) {
			return applicationContext.getAutowireCapableBeanFactory().createBean(InfluxDBCollector.class);
		} else if (baseURL.toLowerCase().startsWith("jdbc")) {
			return applicationContext.getAutowireCapableBeanFactory().createBean(JDBCCollector.class);
		} else if (baseURL.equalsIgnoreCase("micrometer")) {
			return applicationContext.getAutowireCapableBeanFactory().createBean(Micrometer.class);
		} else {
			throw new IllegalArgumentException(String.format("Base url for statistics contains an unrecognized values, baseURL %s.", baseURL));
		}
	}
	
}
