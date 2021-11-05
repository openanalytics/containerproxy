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
package eu.openanalytics.containerproxy.api;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

public class BaseController {

	@Inject
	private Environment environment;

	protected void prepareMap(ModelMap map) {
		map.put("title", environment.getProperty("proxy.title", "ShinyProxy"));
		map.put("bootstrapCss", "/webjars/bootstrap/3.4.1/css/bootstrap.min.css");
		map.put("bootstrapJs", "/webjars/bootstrap/3.4.1/js/bootstrap.min.js");
		map.put("jqueryJs", "/webjars/jquery/3.5.1/jquery.min.js");
		map.put("fontAwesomeCss", "/webjars/fontawesome/4.7.0/css/font-awesome.min.css");
		map.put("bootstrapSocialCss", "/webjars/bootstrap-social/5.1.1/bootstrap-social.css");
	}

	public static class NotFoundException extends RuntimeException {
		
		private static final long serialVersionUID = 2042632906716154791L;

		public NotFoundException(String message) {
			super(message);
		}

	}
	
	@ControllerAdvice
	public static class RestErrorHandler {
	    @ExceptionHandler(NotFoundException.class)
	    @ResponseStatus(HttpStatus.NOT_FOUND)
	    @ResponseBody
	    public Object notFound(NotFoundException ex) {
	        return ex.getMessage();
	    }
	}
}
