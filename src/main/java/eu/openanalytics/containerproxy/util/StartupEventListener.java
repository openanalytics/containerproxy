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
package eu.openanalytics.containerproxy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class StartupEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupEventListener.class);

    @Inject
    private BuildProperties buildProperties;

    @EventListener
    public void onStartup(ApplicationReadyEvent event) {
        String startupMsg = "Started " + buildProperties.getName() + " " +
            buildProperties.getVersion() + " (" +
            "ContainerProxy " +
            buildProperties.get("containerProxyVersion") + ")";
        LOGGER.info(startupMsg);
    }
}
