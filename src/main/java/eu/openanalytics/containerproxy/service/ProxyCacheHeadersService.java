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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CacheHeadersMode;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.CacheHeadersModeKey;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProxyCacheHeadersService {

    private static final String EXPIRES = "Expires";
    private static final String PRAGMA = "Pragma";
    private static final String CACHE_CONTROL = "Cache-Control";

    private final static List<Pair<HttpString, String>> noCacheHeaders = createNoCacheHeaders();
    private final static List<Pair<HttpString, String>> cacheHeaders = createCacheHeaders();

    // media types that corresponds to a CSS/JS/Font asset
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types
    // https://www.iana.org/assignments/media-types/media-types.xhtml#font
    private final static MediaType APPLICATION_JS = new MediaType("application", "javascript");
    private final static MediaType TEXT_JS = new MediaType("text", "javascript");
    private final static MediaType TEXT_CSS = new MediaType("text", "css");
    private final static MediaType APPLICATION_FONT_WOFF = new MediaType("application", "font-woff");
    private final static MediaType APPLICATION_FONT_WOFF2 = new MediaType("application", "font-woff2");
    private final static MediaType APPLICATION_FONT_SFNT = new MediaType("application", "font-sfnt");
    private final static MediaType APPLICATION_FONT_TDPR = new MediaType("application", "font-tdpfr");

    private static final List<MediaType> ASSET_MIME_TYPES = List.of(APPLICATION_JS, TEXT_JS, TEXT_CSS,
        APPLICATION_FONT_WOFF, APPLICATION_FONT_WOFF2, APPLICATION_FONT_SFNT, APPLICATION_FONT_TDPR);

    public void addAppCacheHeaders(Proxy proxy, HttpServerExchange exchange) {
        CacheHeadersMode mode = proxy.getRuntimeObject(CacheHeadersModeKey.inst);
        if (mode.equals(CacheHeadersMode.EnforceNoCache)) {
            // enforce no-cache on all assets
            writeNoCacheHeaders(exchange);
        } else if (mode.equals(CacheHeadersMode.Passthrough)) {
            // trust any cache headers added by the app, do nothing
        } else if (mode.equals(CacheHeadersMode.EnforceCacheAssets)) {
            if (isAsset(exchange)) {
                // it as an asset -> enforce cache
                writeCacheHeaders(exchange);
            } else {
                // otherwise, enforce no-cache
                writeNoCacheHeaders(exchange);
            }
        }
    }

    private boolean isAsset(HttpServerExchange exchange) {
        if (!exchange.getRequestMethod().equals(Methods.GET)) {
            return false;
        }
        try {
            String contentType = exchange.getResponseHeaders().get("Content-Type", 0);
            if (contentType == null) {
                return false;
            }
            MediaType mediaType = MediaType.parseMediaType(contentType);
            // use equalsTypeAndSubtype to ignore e.g. charset
            if (mediaType.getType().equals("font")
                || ASSET_MIME_TYPES.stream().anyMatch(m -> m.equalsTypeAndSubtype(mediaType))) {
                return true;
            }
        } catch (IndexOutOfBoundsException ex) {
            return false;
        }
        return false;
    }

    private void writeNoCacheHeaders(HttpServerExchange exchange) {
        for (Pair<HttpString, String> header : noCacheHeaders) {
            exchange.getResponseHeaders().put(header.getFirst(), header.getSecond());
        }
    }

    private void writeCacheHeaders(HttpServerExchange exchange) {
        // first remove any header added by the app that disables caching
        for (Pair<HttpString, String> header : noCacheHeaders) {
            exchange.getResponseHeaders().remove(header.getFirst());
        }
        for (Pair<HttpString, String> header : cacheHeaders) {
            exchange.getResponseHeaders().put(header.getFirst(), header.getSecond());
        }
    }

    private static List<Pair<HttpString, String>> createNoCacheHeaders() {
        List<Pair<HttpString, String>> headers = new ArrayList<>(3);
        headers.add(Pair.of(new HttpString(CACHE_CONTROL), "no-cache, no-store, max-age=0, must-revalidate"));
        headers.add(Pair.of(new HttpString(PRAGMA), "no-cache"));
        headers.add(Pair.of(new HttpString(EXPIRES), "0"));
        return headers;
    }

    private static List<Pair<HttpString, String>> createCacheHeaders() {
        List<Pair<HttpString, String>> headers = new ArrayList<>(1);
        headers.add(Pair.of(new HttpString(CACHE_CONTROL), "max-age=86400"));
        return headers;
    }

}
