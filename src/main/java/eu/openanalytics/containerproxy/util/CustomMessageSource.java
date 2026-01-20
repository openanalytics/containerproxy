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
package eu.openanalytics.containerproxy.util;

import eu.openanalytics.containerproxy.service.LanguageService;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Component("messageSource")
public class CustomMessageSource extends AbstractMessageSource {

    private static final String PROP_PROXY_TRANSLATION_OVERRIDES = "proxy.translation-overrides";
    private final Map<String, Map<String, String>> translationOverrides = new HashMap<>();
    private final Map<String, Map<String, MessageFormat>> translationOverridesMessageFormat = new HashMap<>();

    public CustomMessageSource(Environment environment, LanguageService languageService) {
        ResourceBundleMessageSource parentMessageSource = new CapitalizedMessageSource();
        parentMessageSource.setBasenames("messages", "cp_messages");
        parentMessageSource.setAlwaysUseMessageFormat(true);
        setParentMessageSource(parentMessageSource);

        // read overridden translations from config file
        for (String language : languageService.getEnabledLanguages().keySet()) {
            Map<String, String> messages = getTranslationOverrides(environment, language);
            translationOverrides.put(language, messages);
            translationOverridesMessageFormat.put(language, convertToMessageFormat(messages, language));
        }
    }

    @Override
    protected @Nullable String resolveCodeWithoutArguments(@Nonnull String code, @Nonnull Locale locale) {
        if (translationOverrides.containsKey(locale.getLanguage())) {
            return translationOverrides.get(locale.getLanguage()).get(code);
        }
        return null;
    }

    @Override
    protected @Nullable MessageFormat resolveCode(@Nonnull String code, @Nonnull Locale locale) {
        if (translationOverrides.containsKey(locale.getLanguage())) {
            return translationOverridesMessageFormat.get(locale.getLanguage()).get(code);
        }
        return null;
    }

    private Map<String, String> getTranslationOverrides(Environment environment, String language) {
        return Binder.get(environment)
            .bind(PROP_PROXY_TRANSLATION_OVERRIDES + "." + language, Bindable.mapOf(String.class, String.class))
            .orElse(new HashMap<>());
    }

    private Map<String, MessageFormat> convertToMessageFormat(Map<String, String> messages, String language) {
        Locale locale = Locale.forLanguageTag(language);
        return messages.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> new MessageFormat(StringUtils.capitalize(e.getValue()), locale))
        );
    }

    private static class CapitalizedMessageSource extends ResourceBundleMessageSource {

        /**
         * Capitalizes the first letter of each string. Because this low-level method
         * is overridden, the strings are still properly cached.
         *
         * @param bundle the ResourceBundle to perform the lookup in
         * @param key    the key to look up
         * @return the associated value, or {@code null} if none
         */
        @Override
        protected @Nullable String getStringOrNull(@Nonnull ResourceBundle bundle, @Nonnull String key) {
            String result = super.getStringOrNull(bundle, key);
            if (result != null) {
                return StringUtils.capitalize(result);
            }
            return null;
        }

    }

}
