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

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.api.BaseController;
import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import org.keycloak.adapters.OIDCAuthenticationError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

import static eu.openanalytics.containerproxy.auth.impl.keycloak.AuthenticationFaillureHandler.SP_KEYCLOAK_ERROR_REASON;

@Controller
@RequestMapping("/error")
public class ErrorController extends BaseController implements org.springframework.boot.web.servlet.error.ErrorController {

	@RequestMapping(produces = "text/html")
	public String handleError(ModelMap map, HttpServletRequest request, HttpServletResponse response) {

		// handle keycloak errors
	    Object obj = request.getSession().getAttribute(SP_KEYCLOAK_ERROR_REASON);
	    if (obj instanceof OIDCAuthenticationError.Reason) {
	    	request.getSession().removeAttribute(SP_KEYCLOAK_ERROR_REASON);
			OIDCAuthenticationError.Reason reason = (OIDCAuthenticationError.Reason) obj;
	    	if (reason == OIDCAuthenticationError.Reason.INVALID_STATE_COOKIE ||
				reason == OIDCAuthenticationError.Reason.STALE_TOKEN) {
	    		// These errors are typically caused by users using wrong bookmarks (e.g. bookmarks with states in)
				// or when some cookies got stale. However, the user is logged into the IDP, therefore it's enough to
				// send the user to the main page and they will get logged in automatically.
				return "redirect:/";
			} else {
				return "redirect:/auth-error";
			}
		}

		Optional<Throwable> exception = getException(request);
		if (response.getStatus() == 200 && exception.isPresent() && isAccountStatusException(exception.get())) {
			return "redirect:/";
		}

		String shortError = "ShinyProxy experienced an unrecoverable error.";
		String description = "";

		if (exception.isPresent() && exception.get() instanceof RequestRejectedException) {
			shortError = "Bad Request";
			description = "You are not allowed to send this request.";
			response.setStatus(400);
		}

		if (exception.isPresent() && exception.get() instanceof ContainerProxyException) {
			description = exception.get().getMessage();
		}

		Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (status != null) {
			int statusCode = Integer.parseInt(status.toString());
			if (statusCode == HttpStatus.NOT_FOUND.value()) {
				shortError = "Not found";
				description = "The requested page was not found";
			} else if (statusCode == HttpStatus.FORBIDDEN.value()) {
				shortError = "Forbidden";
				description = "You do not have access to this page";
			} else if (statusCode == HttpStatus.METHOD_NOT_ALLOWED.value()) {
				shortError = "Method not allowed";
			}
		}

		prepareMap(map);
		map.put("mainPage", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
		map.put("shortError", shortError);
		map.put("description", description);
		
		return "error";
	}
	
	@RequestMapping(produces = "application/json")
	@ResponseBody
	public ResponseEntity<ApiResponse<Object>> error(HttpServletRequest request, HttpServletResponse response) {
		Optional<Throwable> exception = getException(request);
		if (response.getStatus() == 200 && exception.isPresent() && isAccountStatusException(exception.get())) {
			return ApiResponse.failUnauthorized();
		}

		if (exception.isPresent() && exception.get() instanceof RequestRejectedException) {
			return ApiResponse.fail("bad request");
		}

		Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (status != null) {
			int statusCode = Integer.parseInt(status.toString());
			if (statusCode == HttpStatus.NOT_FOUND.value()) {
				return ApiResponse.failNotFound();
			} else if (statusCode == HttpStatus.FORBIDDEN.value()) {
				return ApiResponse.failForbidden();
			} else if (statusCode == HttpStatus.METHOD_NOT_ALLOWED.value()) {
				return ResponseEntity.status(HttpStatus.METHOD_FAILURE).body(new ApiResponse<>("fail", "method not allowed"));
			}
	}

		return ApiResponse.error("unrecoverable error");
	}

	private boolean isAccountStatusException(Throwable exception) {
		if (exception instanceof AccountStatusException) return true;
		if (exception.getCause() != null) return isAccountStatusException(exception.getCause());
		return false;
	}

	private Optional<Throwable> getException(HttpServletRequest request) {
		Throwable exception = (Throwable) request.getAttribute("javax.servlet.error.exception");
		if (exception == null) {
			exception = (Throwable) request.getAttribute("SPRING_SECURITY_LAST_EXCEPTION");
		}
		return Optional.ofNullable(exception);
	}

}
