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

import eu.openanalytics.containerproxy.model.runtime.Proxy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileLogStorage extends AbstractLogStorage {

	@Override
	public void initialize() throws IOException {
		super.initialize();
		Files.createDirectories(Paths.get(containerLogPath));
	}
	
	@Override
	public LogStreams createOutputStreams(Proxy proxy) throws IOException {
		// TODO buffer
		LogPaths paths = getLogs(proxy);
		return new LogStreams(
				Files.newOutputStream(paths.getStdout()),
				Files.newOutputStream(paths.getStderr())
		);
	}

}
