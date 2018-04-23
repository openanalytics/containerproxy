package eu.openanalytics.containerproxy.api;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;

public class BaseController {

	protected String getUserName(HttpServletRequest request) {
		Principal principal = request.getUserPrincipal();
		String username = (principal == null) ? request.getSession().getId() : principal.getName();
		return username;
	}

}
