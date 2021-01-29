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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.backend.strategy.IProxyLogoutStrategy;
import eu.openanalytics.containerproxy.event.AuthFailedEvent;
import eu.openanalytics.containerproxy.event.UserLoginEvent;
import eu.openanalytics.containerproxy.event.UserLogoutEvent;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.SessionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.session.Session;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
public class UserService {

	private final static String ATTRIBUTE_USER_INITIATED_LOGOUT = "SP_USER_INITIATED_LOGOUT";

	private final Logger log = LogManager.getLogger(UserService.class);

	@Inject
	private Environment environment;

	@Inject
	@Lazy
	// Note: lazy needed to work around early initialization conflict 
	private IAuthenticationBackend authBackend;
	
	@Inject
	private IProxyLogoutStrategy logoutStrategy;

	@Inject
	private ApplicationEventPublisher applicationEventPublisher;

	public Authentication getCurrentAuth() {
		return SecurityContextHolder.getContext().getAuthentication();
	}
	
	public String getCurrentUserId() {
		return getUserId(getCurrentAuth());
	}
	
	public String[] getAdminGroups() {
		Set<String> adminGroups = new HashSet<>();
		
		// Support for old, non-array notation
		String singleGroup = environment.getProperty("proxy.admin-groups");
		if (singleGroup != null && !singleGroup.isEmpty()) adminGroups.add(singleGroup.toUpperCase());
		
		for (int i=0 ;; i++) {
			String groupName = environment.getProperty(String.format("proxy.admin-groups[%s]", i));
			if (groupName == null || groupName.isEmpty()) break;
			adminGroups.add(groupName.toUpperCase());
		}

		return adminGroups.toArray(new String[adminGroups.size()]);
	}
	
	public String[] getGroups() {
		return getGroups(getCurrentAuth());
	}
	
	public String[] getGroups(Authentication auth) {
		List<String> groups = new ArrayList<>();
		if (auth != null) {
			for (GrantedAuthority grantedAuth: auth.getAuthorities()) {
				String authName = grantedAuth.getAuthority().toUpperCase();
				if (authName.startsWith("ROLE_")) authName = authName.substring(5);
				groups.add(authName);
			}
		}
		return groups.toArray(new String[groups.size()]);
	}
	
	public boolean isAdmin() {
		return isAdmin(getCurrentAuth());
	}
	
	public boolean isAdmin(Authentication auth) {
		for (String adminGroup: getAdminGroups()) {
			if (isMember(auth, adminGroup)) return true;
		}
		return false;
	}
	
	public boolean canAccess(ProxySpec spec) {
		return canAccess(getCurrentAuth(), spec);
	}
	
	public boolean canAccess(Authentication auth, ProxySpec spec) {
		if (auth == null || spec == null) return false;
		if (auth instanceof AnonymousAuthenticationToken) return !authBackend.hasAuthorization();

		if (spec.getAccessControl() == null) return true;
		
		String[] groups = spec.getAccessControl().getGroups();
		if (groups == null || groups.length == 0) return true;
		for (String group: groups) {
			if (isMember(auth, group)) return true;
		}
		return false;
	}
	
	public boolean isOwner(Proxy proxy) {
		return isOwner(getCurrentAuth(), proxy);
	}

	public boolean isOwner(Authentication auth, Proxy proxy) {
		if (auth == null || proxy == null) return false;
		return proxy.getUserId().equals(getUserId(auth));
	}
	
	private boolean isMember(Authentication auth, String groupName) {
		if (auth == null || auth instanceof AnonymousAuthenticationToken || groupName == null) return false;
		for (String group: getGroups(auth)) {
			if (group.equalsIgnoreCase(groupName)) return true;
		}
		return false;
	}

	private String getUserId(Authentication auth) {
		if (auth == null) return null;
		if (auth instanceof AnonymousAuthenticationToken) {
			// Anonymous authentication: use the session id instead of the user name.
			return SessionHelper.getCurrentSessionId(true);
		}
		return auth.getName();
	}

	@EventListener
	public void onAbstractAuthenticationFailureEvent(AbstractAuthenticationFailureEvent event) {
		Authentication source = event.getAuthentication();
		Exception e = event.getException();
		log.info(String.format("Authentication failure [user: %s] [error: %s]", source.getName(), e.getMessage()));
		String userId = getUserId(source);

		applicationEventPublisher.publishEvent(new AuthFailedEvent(
				this,
				userId,
				RequestContextHolder.currentRequestAttributes().getSessionId()));
	}

	public void logout(Authentication auth) {
		// TODO test for anonymous users
		String userId = getUserId(auth);
		if (userId == null) return;

		if (logoutStrategy != null) logoutStrategy.onLogout(userId);
		log.info(String.format("User logged out [user: %s]", userId));

		HttpSession session = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getSession();
		session.setAttribute(ATTRIBUTE_USER_INITIATED_LOGOUT, "true"); // mark that the user initiated the logout


		String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();
		applicationEventPublisher.publishEvent(new UserLogoutEvent(
				this,
				userId,
				sessionId,
				false));
	}

	@EventListener
	public void onAuthenticationSuccessEvent(AuthenticationSuccessEvent event) {
		Authentication auth = event.getAuthentication();
		String userName = auth.getName();

		log.info(String.format("User logged in [user: %s]", userName));

		// TODO test for anonymous users
		String userId = getUserId(auth);
		applicationEventPublisher.publishEvent(new UserLoginEvent(
				this,
				userId,
				RequestContextHolder.currentRequestAttributes().getSessionId()));
	}

	@EventListener
	public void onHttpSessionDestroyedEvent(HttpSessionDestroyedEvent event) {
		String userInitiatedLogout = (String) event.getSession().getAttribute(ATTRIBUTE_USER_INITIATED_LOGOUT);

		if (userInitiatedLogout != null && userInitiatedLogout.equals("true")) {
			// user initiated the logout
			// event already handled by the logout() function above -> ignore it
		} else {
			// user did not initiated the logout -> session expired
			// not already handled by any other handler
			if (!event.getSecurityContexts().isEmpty()) {
				SecurityContext securityContext = event.getSecurityContexts().get(0);
				if (securityContext == null) return;

				Authentication authentication = securityContext.getAuthentication();
				String userId = ((User) authentication.getPrincipal()).getUsername();

				applicationEventPublisher.publishEvent(new UserLogoutEvent(
						this,
						userId,
						event.getSession().getId(),
						true
				));
			}
		}
	}

}
