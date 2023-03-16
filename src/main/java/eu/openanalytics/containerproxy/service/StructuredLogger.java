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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class StructuredLogger {

    private final Logger logger;

    public StructuredLogger(Logger logger) {
        this.logger = logger;
    }

    public static <T> StructuredLogger create(Class<T> clazz) {
        return new StructuredLogger(LoggerFactory.getLogger(clazz));
    }

    public void info(Proxy proxy, String message) {
        if (proxy == null) {
            logger.info(message);
        } else {
            logger.info("[{} {} {}] " + message, kv("user", proxy.getUserId()), kv("proxyId", proxy.getId()), kv("specId", proxy.getSpecId()));
        }
    }

    public void error(Proxy proxy, Throwable throwable, String message) {
        if (proxy == null) {
            logger.error(message, throwable);
        } else {
            // https://stackoverflow.com/a/6374166/1393103 throwable as last argument will be interpreted as a throwable
            logger.error("[{} {} {}] " + message, kv("user", proxy.getUserId()), kv("proxyId", proxy.getId()), kv("specId", proxy.getSpecId()), throwable);
        }
    }

    public void warn(Proxy proxy, Throwable throwable, String message) {
        if (proxy == null) {
            logger.warn(message, throwable);
        } else {
            // https://stackoverflow.com/a/6374166/1393103 throwable as last argument will be interpreted as a throwable
            logger.warn("[{} {} {}] " + message, kv("user", proxy.getUserId()), kv("proxyId", proxy.getId()), kv("specId", proxy.getSpecId()), throwable);
        }
    }

    public void warn(Proxy proxy, String message) {
        if (proxy == null) {
            logger.warn(message);
        } else {
            // https://stackoverflow.com/a/6374166/1393103 throwable as last argument will be interpreted as a throwable
            logger.warn("[{} {} {}] " + message, kv("user", proxy.getUserId()), kv("proxyId", proxy.getId()), kv("specId", proxy.getSpecId()));
        }
    }

    public void debug(Proxy proxy, String message) {
        if (proxy == null) {
            logger.debug(message);
        } else {
            logger.debug("[{} {} {}] " + message, kv("user", proxy.getUserId()), kv("proxyId", proxy.getId()), kv("specId", proxy.getSpecId()));
        }
    }

}
