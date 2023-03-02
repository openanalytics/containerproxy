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
package eu.openanalytics.containerproxy.auth.impl.saml;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.saml2.provider.service.metadata.Saml2MetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Nonnull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static eu.openanalytics.containerproxy.auth.impl.saml.SAMLConfiguration.SAML_METADATA_PATH;

/**
 * Serves the SAML metadata on the fixed /saml/metadata path.
 * We cannot use the original filter because this requires to have the registrationId as part of the URL.
 */
public final class Saml2MetadataFilter extends OncePerRequestFilter {

	public static final String DEFAULT_METADATA_FILE_NAME = "spring_saml_metadata.xml";

	private final Saml2MetadataResolver saml2MetadataResolver;
	private final RelyingPartyRegistration relyingPartyRegistration;

	private final RequestMatcher requestMatcher = new AntPathRequestMatcher(SAML_METADATA_PATH);

	public Saml2MetadataFilter(RelyingPartyRegistration relyingPartyRegistrationResolver,
							   Saml2MetadataResolver saml2MetadataResolver) {
		this.relyingPartyRegistration = relyingPartyRegistrationResolver;
		this.saml2MetadataResolver = saml2MetadataResolver;
	}

	@Override
	protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain chain)
			throws ServletException, IOException {
		RequestMatcher.MatchResult matcher = this.requestMatcher.matcher(request);
		if (!matcher.isMatch()) {
			chain.doFilter(request, response);
			return;
		}
		String metadata = this.saml2MetadataResolver.resolve(relyingPartyRegistration);
		writeMetadataToResponse(response, metadata);
	}

	private void writeMetadataToResponse(HttpServletResponse response, String metadata)
			throws IOException {
		response.setContentType(MediaType.APPLICATION_XML_VALUE);
		String encodedFileName = URLEncoder.encode(DEFAULT_METADATA_FILE_NAME, StandardCharsets.UTF_8.name());
		String format = "attachment; filename=\"%s\"; filename*=UTF-8''%s";
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, String.format(format, DEFAULT_METADATA_FILE_NAME, encodedFileName));
		response.setContentLength(metadata.length());
		response.getWriter().write(metadata);
	}

}
