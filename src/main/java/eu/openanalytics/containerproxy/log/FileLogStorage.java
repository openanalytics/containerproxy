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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

public class FileLogStorage extends AbstractLogStorage {

	@Override
	public void initialize() throws IOException {
		super.initialize();
		Files.createDirectories(Paths.get(containerLogPath));
	}
	
	@Override
	public OutputStream[] createOutputStreams(Proxy proxy) throws IOException {
		String[] paths = getLogs(proxy);
		return new OutputStream[] {
				new FileOutputStream(paths[0]),
				new FileOutputStream(paths[1])
		};
	}
	
}
