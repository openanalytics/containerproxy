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
package eu.openanalytics.containerproxy.log;

import javax.inject.Inject;

import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service(value="logStorage")
@Primary
public class LogStorageFactory extends AbstractFactoryBean<ILogStorage> {

	@Inject
	private Environment environment;
	
	@Inject
	private ApplicationContext applicationContext;
	
	@Override
	public Class<?> getObjectType() {
		return ILogStorage.class;
	}

	@Override
	protected ILogStorage createInstance() throws Exception {
		ILogStorage storage = null;
		
		String containerLogPath = environment.getProperty("proxy.container-log-path");
		if (containerLogPath == null || containerLogPath.trim().isEmpty()) {
			storage = new NoopLogStorage();
		} else if (containerLogPath.toLowerCase().startsWith("s3://")) {
			storage = new S3LogStorage();
		} else {
			storage = new FileLogStorage();
		}
		
		applicationContext.getAutowireCapableBeanFactory().autowireBean(storage);
		return storage;
	}

}
