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
package eu.openanalytics.containerproxy.util;

import org.springframework.core.env.Environment;

public class SessionHelper {

	/**
	 * Get the context path that has been configured for this instance.
	 * 
	 * @param environment The Spring environment containing the context-path setting.
	 * @param endWithSlash True to always end the context path with a slash.
	 * @return The instance's context path, may be empty, never null.
	 */
	public static String getContextPath(Environment environment, boolean endWithSlash) {
		String contextPath = environment.getProperty("server.servlet.context-path");
		if (contextPath == null || contextPath.trim().equals("/") || contextPath.trim().isEmpty()) return endWithSlash ? "/" : "";
		
		if (!contextPath.startsWith("/")) contextPath = "/" + contextPath;
		if (endWithSlash && !contextPath.endsWith("/")) contextPath += "/";
		
		return contextPath;
	}

}
