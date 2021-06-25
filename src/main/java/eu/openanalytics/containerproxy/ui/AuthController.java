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
package eu.openanalytics.containerproxy.ui;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import eu.openanalytics.containerproxy.api.BaseController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;

@Controller
public class AuthController extends BaseController {

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
		} else {
			return "login";
		}
	}

	@RequestMapping(value = "/auth-error", method = RequestMethod.GET)
	public String getAuthErrorPage(ModelMap map) {
		prepareMap(map);
		map.put("application_name", environment.getProperty("spring.application.name"));
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