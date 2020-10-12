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
package eu.openanalytics.containerproxy.util;

import java.util.Optional;

import javax.servlet.http.HttpSession;

import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.context.SecurityContext;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.HeaderValues;

public class SessionHelper {

	/**
	 * Looks up the session of the current servlet exchange, and return its ID.
	 * 
	 * @param createIfMissing True to create a session if no session is currently active.
	 * @return The current session ID, or null if no session is active.
	 */
	public static String getCurrentSessionId(boolean createIfMissing) {
		ServletRequestContext context = ServletRequestContext.current();
		if (context == null) return null;
		
		HttpSession session = context.getSession();
		if (session != null) return session.getId();
		
		Cookie jSessionIdCookie = context.getExchange().getRequestCookies().get("JSESSIONID");
		if (jSessionIdCookie != null) return jSessionIdCookie.getValue();
		
		if (createIfMissing) return context.getCurrentServletContext().getSession(context.getExchange(), true).getId();
		else return null;
	}
	
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
	
	/**
	 * Obtain information about the 'owner' of the current HTTP exchange.
	 * This method will try to identify the owner, even if:
	 * 
	 *  <ul>
	 *  <li>There is no servlet context</li>
	 *  <li>There is no authentication backend (users are anonymous)</li>
	 *  </ul>
	 * 
	 * @param exchange The current HTTP exchange.
	 * @return An object containing information about the current user.
	 */
	public static SessionOwnerInfo createOwnerInfo(HttpServerExchange exchange) {
		SessionOwnerInfo info = new SessionOwnerInfo();
		
		// Ideally, use the HTTP session information.
		info.principal = Optional.ofNullable(ServletRequestContext.current())
			.map(ctx -> ctx.getSession())
			.map(session -> (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT"))
			.map(ctx -> ctx.getAuthentication())
			.filter(auth -> !AnonymousAuthenticationToken.class.isInstance(auth))
			.map(auth -> auth.getPrincipal())
			.orElse(null);
		
		// Fallback: use the Authorization header, if present.
		HeaderValues authHeader = exchange.getRequestHeaders().get("Authorization");
		if (authHeader != null) info.authHeader = authHeader.getFirst();

		// Fallback: use the JSESSIONID cookie, if present.
		Cookie jSessionIdCookie = exchange.getRequestCookies().get("JSESSIONID");
		if (jSessionIdCookie != null) info.jSessionId = jSessionIdCookie.getValue();
		
		// Final fallback: generate a JSESSIONID for this exchange.
		// Supports anonymous requests (i.e. authentication: 'none')
		if (info.principal == null && info.authHeader == null && info.jSessionId == null) {
			info.jSessionId = getCurrentSessionId(true);
		}
		
		return info;
	}
	
	public static class SessionOwnerInfo {
		
		public Object principal;
		public String authHeader;
		public String jSessionId;
		
		public boolean isSame(SessionOwnerInfo other) {
			if (principal != null && other.principal != null) {
				//TODO Probably, this check will have to be made dependent on the type of principal (LDAP vs OIDC etc)
				if (principal instanceof AuthenticatedPrincipal && other.principal instanceof AuthenticatedPrincipal) {
					return ((AuthenticatedPrincipal) principal).getName().equals(((AuthenticatedPrincipal) other.principal).getName());
				}
				return principal.equals(other.principal);
			}
			if (authHeader != null && other.authHeader != null) return authHeader.equals(other.authHeader);
			if (jSessionId != null && other.jSessionId != null) return jSessionId.equals(other.jSessionId);
			return false;
		}
	}
}
