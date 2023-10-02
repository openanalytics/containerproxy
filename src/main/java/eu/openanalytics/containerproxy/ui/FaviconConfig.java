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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

@Configuration
public class FaviconConfig {

	private static final String CONTENT_TYPE_ICO = "image/x-icon";
	
	@Inject
    private Environment environment;
	
	@Bean
	@ConditionalOnProperty(name="proxy.favicon-path")
	public SimpleUrlHandlerMapping customFaviconHandlerMapping() {
		byte[] cachedIcon = null;
		
		Path iconPath = Paths.get(environment.getProperty("proxy.favicon-path"));
		if (Files.isRegularFile(iconPath)) {
			try (InputStream input = Files.newInputStream(iconPath)) {
				cachedIcon = FileCopyUtils.copyToByteArray(input);
			} catch (IOException e) {
				throw new IllegalArgumentException("Cannot read favicon: " + iconPath, e);
			} 
		} else {
			LogManager.getLogger(FaviconConfig.class).error("Invalid favicon path: " + iconPath);
		}
		
		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
		mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
		mapping.setUrlMap(Collections.singletonMap("**/favicon.???", new CachedFaviconHttpRequestHandler(cachedIcon, iconPath)));
		return mapping;
	}

	private static class CachedFaviconHttpRequestHandler implements HttpRequestHandler {
		
		private byte[] cachedIcon;
		private Path iconPath;
		
		public CachedFaviconHttpRequestHandler(byte[] cachedIcon, Path iconPath) {
			this.cachedIcon = cachedIcon;
			this.iconPath = iconPath;
		}
		
		@Override
		public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			response.setHeader("Content-Type", getContentType());
			response.setHeader("Content-Length", String.valueOf(cachedIcon.length));
			response.getOutputStream().write(cachedIcon);
			response.getOutputStream().flush();
			response.setStatus(200);
		}
		
		private String getContentType() {
			String fileName = iconPath.getFileName().toString().toLowerCase();
			if (fileName.endsWith(".ico")) return CONTENT_TYPE_ICO;
			
			MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
			return mediaType.toString();
		}
	}

}