/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Configuration
public class TemplateResolverConfig implements WebMvcConfigurer {

    public static final String PROP_CORS_ALLOWED_ORIGINS = "proxy.api-security.cors-allowed-origins";
    private static final String PROP_TEMPLATE_PATH = "proxy.template-path";
    @Inject
    private Environment environment;

    @Inject
    private IdentifierService identifierService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        LoadInMemoryResolver resolver = new LoadInMemoryResolver();
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("file:" + environment.getProperty(PROP_TEMPLATE_PATH) + "/assets/")
            .setOptimizeLocations(true)
            .setUseLastModified(false)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)))
            .resourceChain(true)
            .addResolver(resolver);

        // next line is to have versioned (based on shinyproxy instance) assets
        registry.addResourceHandler("/" + identifierService.instanceId + "/**")
            .addResourceLocations("classpath:/static/")
            .setOptimizeLocations(true)
            .setUseLastModified(false)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)))
            .resourceChain(true)
            .addResolver(resolver);

        registry.addResourceHandler("/" + identifierService.instanceId + "/webjars/**")
            .addResourceLocations("classpath:/META-INF/resources/webjars/")
            .setOptimizeLocations(true)
            .setUseLastModified(false)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)))
            .resourceChain(true)
            .addResolver(resolver);

        registry.addResourceHandler("/" + identifierService.instanceId + "/assets/**")
            .addResourceLocations("file:" + environment.getProperty(PROP_TEMPLATE_PATH) + "/assets/")
            .setOptimizeLocations(true)
            .setUseLastModified(false)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)))
            .resourceChain(true)
            .addResolver(resolver);
    }

    @Bean
    public FileTemplateResolver templateResolver() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(environment.getProperty("proxy.template-path") + "/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
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

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }

    /**
     * A resolver that loads the resource and returns the result as a @link ByteArrayResource.
     * Should be used in combination with @link ResourceChainRegistration where caching is enabled.
     * The ResourceChain will cache this resource and therefore also cache the resource itself.
     */
    private static class LoadInMemoryResolver implements ResourceResolver {
        @Override
        public Resource resolveResource(HttpServletRequest request, @Nonnull String requestPath, @Nonnull List<? extends Resource> locations, ResourceResolverChain chain) {
            Resource resolved = chain.resolveResource(request, requestPath, locations);
            if (resolved == null) {
                return null;
            }
            try {
                return new InMemoryResource(resolved);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String resolveUrlPath(@Nonnull String resourcePath, @Nonnull List<? extends Resource> locations, @Nonnull ResourceResolverChain chain) {
            return null;
        }
    }

    private static class InMemoryResource extends ByteArrayResource {

        private final String filename;
        private final long lastModified;

        public InMemoryResource(Resource resource) throws IOException {
            super(resource.getContentAsByteArray());
            filename = resource.getFilename();
            lastModified = resource.lastModified();
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long lastModified() {
            return lastModified;
        }

    }

}
