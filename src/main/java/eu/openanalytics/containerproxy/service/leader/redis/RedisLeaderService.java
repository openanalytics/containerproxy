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
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.support.locks.ExpirableLockRegistry;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.UUID;

/**
 * Service that can be used to execute code on only one ShinyProxy Server even in HA-mode.
 */
public class RedisLeaderService implements Candidate, ILeaderService {

    @Inject
    private ExpirableLockRegistry lockRegistry;

    @Inject
    private IdentifierService identifierService;

    private volatile boolean isLeader;

    private final Logger logger = LogManager.getLogger(getClass());

    @Scheduled(fixedDelay = 5000)
    private void cleanObsolete() {
        lockRegistry.expireUnusedOlderThan(5000);
    }

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
        isLeader = true;
        logger.info("This server (runtimeId: {}) is now the leader.", identifierService.runtimeId);
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
