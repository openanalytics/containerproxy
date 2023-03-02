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
package eu.openanalytics.containerproxy.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ContextPathHelper {

	private static String contextPathWithoutSlash = null;
	private static String contextPathWithSlash = null;

	@Autowired
	public void setEnvironment(Environment environment){
		contextPathWithSlash = getContextPath(environment, true);
		contextPathWithoutSlash = getContextPath(environment, false);
	}

	public static String withEndingSlash() {
		return contextPathWithSlash;
	}

	public static String withoutEndingSlash() {
		return contextPathWithoutSlash;
	}

	private static String getContextPath(Environment environment, boolean endWithSlash) {
		String contextPath = environment.getProperty("server.servlet.context-path");
		if (contextPath == null || contextPath.trim().equals("/") || contextPath.trim().isEmpty()) return endWithSlash ? "/" : "";

		if (!contextPath.startsWith("/")) contextPath = "/" + contextPath;
		if (endWithSlash && !contextPath.endsWith("/")) contextPath += "/";

		return contextPath;
	}

}
