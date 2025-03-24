/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Controller
public class FaviconController {

    private static final MediaType CONTENT_TYPE_ICO = MediaType.valueOf("image/x-icon");
    private static final List<MediaType> supportedMediaTypes = Arrays.asList(MediaType.IMAGE_PNG, MediaType.IMAGE_GIF, MediaType.IMAGE_JPEG, MediaType.valueOf("image/svg+xml"));

    private final Cache<String, Favicon> faviconCache = Caffeine.newBuilder().build();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final IProxySpecProvider proxySpecProvider;

    private final UserService userService;
    private final Favicon defaultFavicon;

    public FaviconController(Environment environment, IProxySpecProvider proxySpecProvider, UserService userService) {
        this.proxySpecProvider = proxySpecProvider;
        this.userService = userService;
        defaultFavicon = resolveFavicon(environment.getProperty("proxy.favicon-path"));
    }

    @GetMapping({"/favicon.ico", "#{@identifierService.instanceId}/favicon"})
    public ResponseEntity<byte[]> favicon() {
        return toResponse(defaultFavicon);
    }

    @GetMapping("#{@identifierService.instanceId}/favicon/{proxySpecId}")
    public ResponseEntity<byte[]> favicon(@PathVariable String proxySpecId) {
        // Path is prefixed with instanceId as a way of versioning the image.
        // When the configuration changes, the instanceId changes as well
        // and the browser will request the (possible) new image.
        ProxySpec proxySpec = proxySpecProvider.getSpec(proxySpecId);
        if (proxySpec == null) {
            // do not return JSON to not cause issues
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!userService.canAccess(proxySpec)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Favicon favicon = faviconCache.get(proxySpecId, (key) -> {
            Favicon res = resolveFavicon(proxySpec.getFaviconPath());
            if (res == null) {
                res = defaultFavicon;
            }
            return res;
        });

        return toResponse(favicon);
    }

    private Favicon resolveFavicon(String path) {
        if (path == null) {
            return null;
        }

        Path iconPath = Paths.get(path);
        byte[] icon;

        if (Files.isRegularFile(iconPath)) {
            try (InputStream input = Files.newInputStream(iconPath)) {
                icon = FileCopyUtils.copyToByteArray(input);
            } catch (IOException e) {
                logger.warn(String.format("Error while reading favicon %s", path), e);
                return null;
            }
        } else {
            logger.warn(String.format("Error while reading favicon %s: not a regular file", path));
            return null;
        }

        MediaType contentType = getContentType(path);
        if (contentType == null) {
            return null;
        }

        return new Favicon(icon, contentType);
    }

    private ResponseEntity<byte[]> toResponse(Favicon favicon) {
        if (favicon == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .contentType(favicon.contentType)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
            .body(favicon.favicon);
    }

    private MediaType getContentType(String path) {
        Path iconPath = Paths.get(path);
        String fileName = iconPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".ico")) {
            return CONTENT_TYPE_ICO;
        }
        Optional<MediaType> mediaType = MediaTypeFactory.getMediaType(path);
        if (mediaType.isEmpty()) {
            logger.warn(String.format("Content-Type of favicon: %s not recognized", path));
            return null;
        }

        if (!supportedMediaTypes.contains(mediaType.get())) {
            logger.warn(String.format("Content-Type of favicon: %s %s not supported", path, mediaType.get()));
            return null;
        }

        return mediaType.get();
    }

    @Data
    @AllArgsConstructor
    public static class Favicon {
        byte[] favicon;
        MediaType contentType;
    }

}
