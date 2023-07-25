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
package eu.openanalytics.containerproxy.ui;

import eu.openanalytics.containerproxy.api.BaseController;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.SAMLAuthenticationBackend;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@Controller
public class AuthController extends BaseController {

	public static final String AUTH_SUCCESS_URL = "/auth-success";
	public static final String AUTH_SUCCESS_URL_SESSION_ATTR = "AUTH_SUCCESS_URL_SESSION_ATTR";

	@Inject
	private Environment environment;

	@Inject
	private IAuthenticationBackend auth;

	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public Object getLoginPage(@RequestParam Optional<String> error, ModelMap map) {
		prepareMap(map);
		if (error.isPresent()) {
			if (error.get().equals("expired")) {
				map.put("error", "You took too long to login, please try again");
			} else {
				map.put("error", "Invalid user name or password");
			}
		}

		if (auth instanceof OpenIDAuthenticationBackend) {
            return new RedirectView(((OpenIDAuthenticationBackend) auth).getLoginRedirectURI());
        } else if (auth instanceof SAMLAuthenticationBackend) {
            return new RedirectView(((SAMLAuthenticationBackend) auth).getLoginRedirectURI());
		} else {
			return "login";
		}
	}

	@RequestMapping(value = AUTH_SUCCESS_URL, method = RequestMethod.GET)
	public String authSuccess(ModelMap map, HttpServletRequest request) {
		prepareMap(map);
		map.put("url", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()); // default url

		Object redirectUrl = request.getSession().getAttribute(AUTH_SUCCESS_URL_SESSION_ATTR);
		if (redirectUrl instanceof String) {
			request.getSession().removeAttribute(AUTH_SUCCESS_URL_SESSION_ATTR);
			String sRedirectUrl = (String) redirectUrl;
			// sanity check: does the redirect url start with the url of this current request
			if (sRedirectUrl.startsWith(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString())) {
				map.put("url", redirectUrl);
			}
		}
		return "auth-success";
	}

	@RequestMapping(value = "/auth-error", method = RequestMethod.GET)
	public String getAuthErrorPage(ModelMap map) {
		prepareMap(map);
		map.put("application_name", environment.getProperty("spring.application.name"));
		map.put("mainPage", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
		return "auth-error";
	}

	@RequestMapping(value = "/app-access-denied", method = RequestMethod.GET)
	public String getAppAccessDeniedPage(ModelMap map) {
		prepareMap(map);
		return "app-access-denied";
	}

	@RequestMapping(value = "/logout-success", method = RequestMethod.GET)
	public String getLogoutSuccessPage(ModelMap map) {
		prepareMap(map);
		return "logout-success";
	}

}
