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
package eu.openanalytics.containerproxy.log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.inject.Inject;

import org.springframework.core.env.Environment;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

public abstract class AbstractLogStorage implements ILogStorage {

	private static final String PARAM_LOG_PATHS = "log_paths";
	
	@Inject
	protected Environment environment;
	
	protected String containerLogPath;
	
	@Override
	public void initialize() throws IOException {
		containerLogPath = environment.getProperty("proxy.container-log-path");
	}
	
	@Override
	public String getStorageLocation() {
		return containerLogPath;
	}

	@Override
	public String[] getLogs(Proxy proxy) throws IOException {
		String[] paths = (String[]) proxy.getContainers().get(0).getParameters().get(PARAM_LOG_PATHS);
		if (paths == null) {
			String timestamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
			paths = new String[] {
					String.format("%s/%s_%s_%s_stdout.log", containerLogPath, proxy.getSpec().getId(), proxy.getId(), timestamp),
					String.format("%s/%s_%s_%s_stderr.log", containerLogPath, proxy.getSpec().getId(), proxy.getId(), timestamp)
			};
			proxy.getContainers().get(0).getParameters().put(PARAM_LOG_PATHS, paths);
		}
		return paths;
	}
}
