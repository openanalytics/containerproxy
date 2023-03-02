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
package eu.openanalytics.containerproxy.util;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.RollingPolicy;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.stacktrace.ShortenedThrowableConverter;
import org.slf4j.ILoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationListener;

/**
 * Configures the logger to (optionally) log as JSON.
 * This uses the programmatic way of configuring logback instead of XML files in order to not override any existing
 * configuration of Spring (e.g. log levels).
 * See {@link org.springframework.boot.logging.logback.DefaultLogbackConfiguration}
 */
public class LoggingConfigurer implements ApplicationListener<ApplicationPreparedEvent> {

    private static final String PROP_LOG_AS_JSON = "proxy.log-as-json";

    public void onApplicationEvent(ApplicationPreparedEvent event) {
        // this event is called after the loggers are configured but before anything is logged
        Boolean logAsJson = event.getApplicationContext().getEnvironment().getProperty(PROP_LOG_AS_JSON, Boolean.class, false);

        if (logAsJson) {
            ILoggerFactory factory = StaticLoggerBinder.getSingleton().getLoggerFactory();
            LoggerContext context = (LoggerContext) factory;
            Logger logger = context.getLogger(LoggingSystem.ROOT_LOGGER_NAME);

            setupConsoleAppender(context, logger);
            setupFileAppender(context, logger);
        }
    }

    private void setupConsoleAppender(LoggerContext context, Logger rootLogger) {
        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setEncoder(createEncoder());
        appender.setName("CONSOLE");
        appender.setContext(context);

        appender.start();

        rootLogger.detachAppender("CONSOLE");
        rootLogger.addAppender(appender);
    }

    private void setupFileAppender(LoggerContext context, Logger rootLogger) {
        Appender<ILoggingEvent> oldAppender = rootLogger.getAppender("FILE");
        if (oldAppender instanceof RollingFileAppender) {
            oldAppender.stop();

            RollingFileAppender<ILoggingEvent> oldFileAppender = (RollingFileAppender<ILoggingEvent>) oldAppender;
            RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
            appender.setEncoder(createEncoder());
            appender.setName("FILE");
            appender.setContext(context);
            appender.setFile(oldFileAppender.getFile());

            RollingPolicy rollingPolicy = oldFileAppender.getRollingPolicy();
            if (rollingPolicy != null) {
                appender.setRollingPolicy(oldFileAppender.getRollingPolicy());
            }
            appender.setTriggeringPolicy(oldFileAppender.getTriggeringPolicy());

            appender.getTriggeringPolicy().start();
            appender.start();

            rootLogger.detachAppender("FILE");
            rootLogger.addAppender(appender);
        }

    }

    private LogstashEncoder createEncoder() {
        ShortenedThrowableConverter throwableConverter = new ShortenedThrowableConverter();
        throwableConverter.addExclude("^java\\.util\\.concurrent\\..*");
        throwableConverter.addExclude("^java\\.lang\\.Thread\\..*");
        throwableConverter.addExclude("^java\\.lang\\.reflect\\..*");
        throwableConverter.addExclude("^jdk\\.internal\\.reflect\\..*");
        throwableConverter.addExclude("^javax\\.servlet\\..*");
        throwableConverter.addExclude("^io\\.fabric8\\.kubernetes\\.client\\.dsl\\..*");
        throwableConverter.addExclude("^io\\.fabric8\\.kubernetes\\.client\\.http\\..*");
        throwableConverter.addExclude("^io\\.fabric8\\.kubernetes\\.client\\.okhttp\\..*");
        throwableConverter.addExclude("^org\\.glassfish\\..*");
        throwableConverter.addExclude("^jersey\\.repackaged\\..*");
        throwableConverter.addExclude("^org\\.xnio\\..*");
        throwableConverter.addExclude("^org\\.jboss\\..*");
        throwableConverter.addExclude("^io\\.undertow\\..*");
        throwableConverter.addExclude("^org\\.springframework\\.web\\.filter\\.OncePerRequestFilter\\..*");
        throwableConverter.addExclude("^org\\.springframework\\.web.\\filter\\.DelegatingFilterProxy\\..*");
        throwableConverter.addExclude("^org\\.springframework\\.security\\.web\\.FilterChainProxy\\..*");
        throwableConverter.addExclude("^org\\.springframework\\.web\\.method\\.support\\..*");
        throwableConverter.addExclude("^org\\.springframework\\.web\\.servlet\\..*");
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setThrowableConverter(throwableConverter);
        encoder.setShortenedLoggerNameLength(40);
        encoder.start();
        return encoder;
    }

}
