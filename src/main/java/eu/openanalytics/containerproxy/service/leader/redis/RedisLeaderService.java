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
package eu.openanalytics.containerproxy.service.leader.redis;


import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.leader.ILeaderService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.UUID;

/**
 * Service that can be used to execute code on only one ShinyProxy Server even in HA-mode.
 */
public class RedisLeaderService implements Candidate, ILeaderService {

    private final Logger logger = LogManager.getLogger(getClass());
    @Inject
    private IdentifierService identifierService;
    @Lazy
    @Inject
    private RedisCheckLatestConfigService redisCheckLatestConfigService;
    private volatile boolean isLeader;

    @Nonnull
    @Override
    public String getRole() {
        return "GlobalExecuteService";
    }

    @Nonnull
    @Override
    public String getId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void onGranted(@Nonnull Context context) {
        // before accepting leadership, check if we are running the latest config
        if (redisCheckLatestConfigService.check()) {
            isLeader = true;
            logger.info("This server (runtimeId: {}) is now the leader.", identifierService.runtimeId);
        } else {
            logger.debug("Ignoring leadership because this is not the latest config.");
        }
    }

    @Override
    public void onRevoked(@Nonnull Context context) {
        isLeader = false;
        logger.info("This server (runtimeId: {}) is no longer the leader.", identifierService.runtimeId);
    }

    @Override
    public boolean isLeader() {
        return isLeader;
    }

}
