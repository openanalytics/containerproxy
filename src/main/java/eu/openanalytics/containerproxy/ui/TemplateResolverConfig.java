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

import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.util.EnvironmentUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.List;

@Configuration
public class TemplateResolverConfig implements WebMvcConfigurer {

	private static final String PROP_TEMPLATE_PATH = "proxy.template-path";

	public static final String PROP_CORS_ALLOWED_ORIGINS = "proxy.api-security.cors-allowed-origins";

	@Inject
    private Environment environment;

	@Inject
	private IdentifierService identifierService;
	
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/assets/**")
				.addResourceLocations("file:" + environment.getProperty(PROP_TEMPLATE_PATH) + "/assets/");
		// next line is to have versioned (based on shinyproxy instance) assets
		registry.addResourceHandler("/" + identifierService.instanceId + "/**")
				.addResourceLocations("classpath:/static/");
		registry.addResourceHandler("/" + identifierService.instanceId + "/webjars/**")
				.addResourceLocations("/webjars/");
		registry.addResourceHandler("/" + identifierService.instanceId + "/assets/**")
				.addResourceLocations("file:" + environment.getProperty(PROP_TEMPLATE_PATH) + "/assets/");
	}

	@Bean
	public FileTemplateResolver templateResolver() {
		FileTemplateResolver resolver = new FileTemplateResolver();
		resolver.setPrefix(environment.getProperty("proxy.template-path") + "/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode("HTML5");
		resolver.setCacheable(false);
		resolver.setCheckExistence(true);
		resolver.setOrder(1);
		return resolver;
	}

	@Override
	public void addCorsMappings(@Nonnull CorsRegistry registry) {
		List<String> origins = EnvironmentUtils.readList(environment, PROP_CORS_ALLOWED_ORIGINS);
		if (origins != null) {
			registry.addMapping("/**")
					.allowCredentials(true)
					.allowedOrigins(origins.toArray(new String[0]));
		}
	}

}
