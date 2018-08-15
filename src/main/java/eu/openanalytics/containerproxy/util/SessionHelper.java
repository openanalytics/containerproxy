/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import javax.servlet.http.HttpSession;

import io.undertow.servlet.handlers.ServletRequestContext;

public class SessionHelper {

	/**
	 * Looks up the session of the current servlet exchange, and return its ID.
	 * 
	 * @param createIfMissing True to create a session if no session is currently active.
	 * @return The current session ID, or null if no session is active.
	 */
	public static String getCurrentSessionId(boolean createIfMissing) {
		ServletRequestContext context = ServletRequestContext.current();
		HttpSession session = context.getSession();
		if (session == null) {
			if (createIfMissing) session = context.getCurrentServletContext().getSession(context.getExchange(), true);
			else return null;
		}
		return session.getId();
	}
}
