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
package eu.openanalytics.containerproxy.util;

import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.StringUtils;

import javax.annotation.Nonnull;
import java.util.Locale;

@Component("messageSource")
@EnableConfigurationProperties(MessageSourceProperties.class)
public class CustomMessageSource implements MessageSource {

    private MessageSource delegate;

    public CustomMessageSource(MessageSourceProperties properties) {
        MessageSourceAutoConfiguration autoConfiguration = new MessageSourceAutoConfiguration();
        this.delegate = autoConfiguration.messageSource(properties);
    }

    @Override
    public String getMessage(@Nonnull String code, Object[] args, String defaultMessage, @Nonnull Locale locale) {
        return StringUtils.capitalize(delegate.getMessage(code, args, defaultMessage, locale));
    }

    @Override
    public String getMessage(@Nonnull String code, Object[] args, @Nonnull Locale locale) throws NoSuchMessageException {
        return StringUtils.capitalize(delegate.getMessage(code, args, locale));
    }

    @Override
    public String getMessage(@Nonnull MessageSourceResolvable resolvable, @Nonnull Locale locale) throws NoSuchMessageException {
        return StringUtils.capitalize(delegate.getMessage(resolvable, locale));
    }

}
