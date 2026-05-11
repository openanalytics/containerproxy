/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2026 Open Analytics
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
import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import eu.openanalytics.containerproxy.auth.impl.SAMLAuthenticationBackend;
import eu.openanalytics.containerproxy.event.AuthFailedEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
public class AuthController extends BaseController {

    public static final String AUTH_SUCCESS_URL = "/auth-success";
    public static final String AUTH_SUCCESS_URL_SESSION_ATTR = "AUTH_SUCCESS_URL_SESSION_ATTR";

    @Inject
    private Environment environment;

    @Inject
    private IAuthenticationBackend auth;

    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    @Inject
    protected MessageSource messageSource;

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public Object getLoginPage(@RequestParam Optional<String> error, ModelMap map) {
        prepareMap(map);
        if (error.isPresent()) {
            Locale locale = LocaleContextHolder.getLocale();
            if (error.get().equals("expired")) {
                map.put("error", messageSource.getMessage("auth.simple.expired_error", null, locale));
            } else {
                map.put("error", messageSource.getMessage("auth.simple.credentials_error", null, locale));
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
        // protocol is added to the url on the client-side
        map.put("url", ServletUriComponentsBuilder.fromCurrentContextPath().path("/").build().toUriString().replace("https://", "//").replace("http://", "//")); // default url

        Object redirectUrl = request.getSession().getAttribute(AUTH_SUCCESS_URL_SESSION_ATTR);
        if (redirectUrl instanceof String sRedirectUrl) {
            request.getSession().removeAttribute(AUTH_SUCCESS_URL_SESSION_ATTR);
            // sanity check: does the redirect url start with the url of this current request
            if (sRedirectUrl.startsWith(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString())) {
                map.put("url", sRedirectUrl.replace("https://", "//").replace("http://", "//"));
            }
        }
        return "auth-success";
    }

    @RequestMapping(value = "/auth-error", method = RequestMethod.GET)
    public String getAuthErrorPage(ModelMap map) {
        applicationEventPublisher.publishEvent(new AuthFailedEvent(this, "user-unknown-reported-by-auth-error-page"));
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


    @ResponseBody
    @GetMapping(value = "/user/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserMetadata() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated();
        if (!isLoggedIn) {
            return ApiResponse.success(
                Map.of("authenticated", false)
            );
        }
        return ApiResponse.success(
            Map.of(
                "authenticated", true,
                "username", authentication.getName()
            )
        );
    }

}
