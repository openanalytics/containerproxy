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
package eu.openanalytics.containerproxy.security;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Disables the handling of OAuth on the `/app_direct` URL.
 * See issue #23799.
 * 
 * Without this, the Filter will eat the body of the (POST) request. As a result Undertow will not be able
 * to proxy the request to the container.
 */
public class DelegatedOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
	
	private final OAuth2AuthorizationRequestResolver delegate;

	public DelegatedOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository, String authorizationRequestBaseUri, boolean pkceAlways) {
		if (pkceAlways) {
			delegate = new AlwaysPkceAuthorizationRequestResolver(clientRegistrationRepository, authorizationRequestBaseUri);
		} else {
			delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, authorizationRequestBaseUri);
		}
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		if (request.getServletPath().startsWith("/app_direct")) {
			return null;
		}
		return delegate.resolve(request);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		if (request.getServletPath().startsWith("/app_direct")) {
			return null;
		}
		return delegate.resolve(request, clientRegistrationId);
	}

}
