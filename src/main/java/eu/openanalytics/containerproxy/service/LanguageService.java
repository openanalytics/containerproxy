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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.util.EnvironmentUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@Service
public class LanguageService {

    private static final String PROP_PROXY_AVAILABLE_LANGUAGES = "proxy.available-languages";
    private static final Map<String, Language> DEFAULT_AVAILABLE_LANGUAGES = Map.of(
        "en", new Language("en", "English"),
        "nl", new Language("nl", "Dutch"),
        "fr", new Language("fr", "French")
    );
    private static final String PROP_PROXY_FALLBACK_LANGUAGE = "proxy.fallback-language";
    private static final String DEFAULT_FALLBACK_LANGUAGE = "en";

    private final Map<String, Language> enabledLanguages;
    private final String fallbackLanguage;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public LanguageService(Environment environment) {
        enabledLanguages = readEnabledLanguages(environment);
        fallbackLanguage = environment.getProperty(PROP_PROXY_FALLBACK_LANGUAGE, DEFAULT_FALLBACK_LANGUAGE);
    }

    public void updateLanguageOfUser(HttpServletRequest request, HttpServletResponse response, String language) {
        if (StringUtils.isBlank(language)) {
            throw new IllegalArgumentException("No language provided");
        }

        if (!enabledLanguages.containsKey(language)) {
            throw new IllegalArgumentException("Requested language not enabled");
        }

        LocaleResolver localeResolver = RequestContextUtils.getLocaleResolver(request);
        if (localeResolver == null) {
            throw new IllegalStateException("Unable to change language");
        }

        localeResolver.setLocale(request, response, Locale.forLanguageTag(language));
    }

    public String getAndUpdateLanguageOfUser(HttpServletRequest request, HttpServletResponse response) {
        String language = LocaleContextHolder.getLocale().getLanguage();
        if (enabledLanguages.containsKey(language)) {
            return language;
        }
        // language of user is not supported -> use fallback language
        updateLanguageOfUser(request, response, fallbackLanguage);
        return fallbackLanguage;
    }

    private Map<String, Language> readEnabledLanguages(Environment environment) {
        List<String> configuredLanguages = EnvironmentUtils.readList(environment, PROP_PROXY_AVAILABLE_LANGUAGES);
        if (configuredLanguages == null) {
            return DEFAULT_AVAILABLE_LANGUAGES;
        }
        Map<String, Language> result = new HashMap<>();
        for (String key : configuredLanguages) {
            Language lang = DEFAULT_AVAILABLE_LANGUAGES.get(key);
            if (lang != null) {
                result.put(lang.key, lang);
            } else {
                logger.warn("Enabled language '{}' is not supported", key);
            }
        }
        return result;
    }

    public Map<String, Language> getEnabledLanguages() {
        return enabledLanguages;
    }

    public record Language(String key, String displayName) {

    }

}
