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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.backend.strategy.IProxyLogoutStrategy;
import eu.openanalytics.containerproxy.event.AuthFailedEvent;
import eu.openanalytics.containerproxy.event.UserLoginEvent;
import eu.openanalytics.containerproxy.event.UserLogoutEvent;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.Sha256;
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
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.session.HttpSessionCreatedEvent;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
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

	@Inject
	@Lazy
	private ProxyAccessControlService accessControlService;

	private final Set<String> adminGroups = new HashSet<>();
	private final Set<String> adminUsers = new HashSet<>();

	@PostConstruct
	public void init() {
		// load admin groups
		// Support for old, non-array notation
		String singleGroup = environment.getProperty("proxy.admin-groups");
		if (singleGroup != null && !singleGroup.isEmpty()) {
			adminGroups.add(singleGroup.toUpperCase());
		}

		for (int i=0 ;; i++) {
			String groupName = environment.getProperty(String.format("proxy.admin-groups[%s]", i));
			if (groupName == null || groupName.isEmpty()) {
				break;
			}
			adminGroups.add(groupName.toUpperCase());
		}

		// load admin users
		// Support for old, non-array notation
		String singleUser = environment.getProperty("proxy.admin-users");
		if (singleUser != null && !singleUser.isEmpty()) {
			adminUsers.add(singleUser);
		}

		for (int i=0 ;; i++) {
			String userName = environment.getProperty(String.format("proxy.admin-users[%s]", i));
			if (userName == null || userName.isEmpty()) {
				break;
			}
			adminUsers.add(userName);
		}
	}

	public Set<String> getAdminGroups() {
		return adminGroups;
	}

	public Authentication getCurrentAuth() {
		return SecurityContextHolder.getContext().getAuthentication();
	}
	
	public String getCurrentUserId() {
		return getUserId(getCurrentAuth());
	}
	
	public Set<String> getAdminUsers() {
		return adminUsers;
	}
	
	public List<String> getGroups() {
		return getGroups(getCurrentAuth());
	}
	
	public static List<String> getGroups(Authentication auth) {
		List<String> groups = new ArrayList<>();
		if (auth != null) {
			for (GrantedAuthority grantedAuth: auth.getAuthorities()) {
				String authName = grantedAuth.getAuthority().toUpperCase();
				if (authName.startsWith("ROLE_")) authName = authName.substring(5);
				groups.add(authName);
			}
		}
		return groups;
	}
	
	public boolean isAdmin() {
		return isAdmin(getCurrentAuth());
	}
	
	public boolean isAdmin(Authentication auth) {
		if (!authBackend.hasAuthorization() || auth == null) {
			return false;
		}

		for (String adminGroup: getAdminGroups()) {
			if (isMember(auth, adminGroup)) {
				return true;
			}
		}

		String userName = getUserId(auth);
		for (String adminUser: getAdminUsers()) {
			if (userName != null && userName.equalsIgnoreCase(adminUser)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean canAccess(ProxySpec spec) {
		return accessControlService.canAccess(getCurrentAuth(), spec);
	}

	public boolean canAccess(Authentication user, ProxySpec spec) {
		return accessControlService.canAccess(user, spec);
	}

	public boolean isOwner(Proxy proxy) {
		return isOwner(getCurrentAuth(), proxy);
	}

	public boolean isOwner(Authentication auth, Proxy proxy) {
		if (auth == null || proxy == null) return false;
		return proxy.getUserId().equals(getUserId(auth));
	}
	
	public boolean isMember(Authentication auth, String groupName) {
		if (auth == null || auth instanceof AnonymousAuthenticationToken || groupName == null) return false;
		for (String group: getGroups(auth)) {
			if (group.equalsIgnoreCase(groupName)) return true;
		}
		return false;
	}

	public static String getUserId(Authentication auth) {
		if (auth == null) return null;
		if (auth instanceof AnonymousAuthenticationToken) {
			// Anonymous authentication: use the session id instead of the username.
			return Sha256.hash(((WebAuthenticationDetails) auth.getDetails()).getSessionId());
		}
		return auth.getName();
	}

	@EventListener
	public void onAbstractAuthenticationFailureEvent(AbstractAuthenticationFailureEvent event) {
		Authentication source = event.getAuthentication();
		Exception e = event.getException();
		log.info(String.format("Authentication failure [user: %s] [error: %s]", getUserId(source), e.getMessage()));
		String userId = getUserId(source);

		applicationEventPublisher.publishEvent(new AuthFailedEvent(
				this,
				userId));
	}

	public void logout(Authentication auth) {
		String userId = getUserId(auth);
		if (userId == null) return;

		if (logoutStrategy != null) logoutStrategy.onLogout(userId);
		log.info(String.format("User logged out [user: %s]", userId));

		HttpSession session = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getSession();
		session.setAttribute(ATTRIBUTE_USER_INITIATED_LOGOUT, "true"); // mark that the user initiated the logout

		applicationEventPublisher.publishEvent(new UserLogoutEvent(
				this,
				userId,
				false));
	}

	@EventListener
	public void onAuthenticationSuccessEvent(AuthenticationSuccessEvent event) {
		Authentication auth = event.getAuthentication();
		String userName = getUserId(auth);

		log.info(String.format("User logged in [user: %s]", userName));

		String userId = getUserId(auth);
		applicationEventPublisher.publishEvent(new UserLoginEvent(
				this,
				userId));
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

				String userId = getUserId(securityContext.getAuthentication());
				if (logoutStrategy != null) logoutStrategy.onLogout(userId);

				log.info(String.format("User logged out [user: %s]", userId));
				applicationEventPublisher.publishEvent(new UserLogoutEvent(
						this,
						userId,
						true
				));
			} else if (authBackend.getName().equals("none")) {
				String userId = Sha256.hash(event.getSession().getId());
				if (logoutStrategy != null) logoutStrategy.onLogout(userId);

				log.info(String.format("Anonymous user logged out [user: %s]", userId));
				applicationEventPublisher.publishEvent(new UserLogoutEvent(
						this,
						userId,
						true
				));
			}
		}
	}

	@EventListener
	public void onHttpSessionCreated(HttpSessionCreatedEvent event) {
		if (authBackend.getName().equals("none")) {
			String userId = Sha256.hash(event.getSession().getId());
			log.info(String.format("Anonymous user logged in [user: %s]", userId));
			applicationEventPublisher.publishEvent(new UserLoginEvent(
					this,
					userId));
		}
	}

}
