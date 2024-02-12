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
package eu.openanalytics.containerproxy.service.leader.redis;

import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.leader.GlobalEventLoopService;
import eu.openanalytics.containerproxy.service.leader.ILeaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Checks whether this server is running the latest configuration, in order to determine whether to take part in the leader election.
 * The checks are scheduled on the {@link GlobalEventLoopService} such that all background processing has finished when this server
 * stops being the leader.
 */
public class RedisCheckLatestConfigService {

    private final LockRegistryLeaderInitiator lockRegistryLeaderInitiator;
    private final RedisTemplate<String, Long> redisTemplate;
    private final IdentifierService identifierService;
    private final GlobalEventLoopService globalEventLoop;
    private final ILeaderService leaderService;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String versionKey;
    private boolean isLatest = false;

    public RedisCheckLatestConfigService(IdentifierService identifierService, LockRegistryLeaderInitiator lockRegistryLeaderInitiator, RedisTemplate<String, Long> redisTemplate, GlobalEventLoopService globalEventLoop, ILeaderService leaderService) {
        this.lockRegistryLeaderInitiator = lockRegistryLeaderInitiator;
        this.redisTemplate = redisTemplate;
        this.identifierService = identifierService;
        this.globalEventLoop = globalEventLoop;
        this.leaderService = leaderService;
        lockRegistryLeaderInitiator.setAutoStartup(false);
        versionKey = "shinyproxy_" + identifierService.realmId + "__version";
    }

    @Async
    @EventListener
    public void init(ApplicationReadyEvent event) {
        if (identifierService.version == null) {
            logger.info("No proxy.version property found, assuming this server is running the latest configuration, taking part in leader election.");
            lockRegistryLeaderInitiator.start();
            return;
        }
        Optional<Boolean> result = redisTemplate.execute(new VersionChecker(versionKey, identifierService.version));
        if (result != null && result.isPresent()) {
            isLatest = result.get();
            if (isLatest) {
                logger.info("This server is running the latest configuration (instanceId: {}, version: {}), taking part in leader election.", identifierService.instanceId, identifierService.version);
                lockRegistryLeaderInitiator.start();
            } else {
                logger.info("This server is not running the latest configuration (instanceId: {}, version: {}), not taking part in leader election.", identifierService.instanceId, identifierService.version);
            }
        } else {
            logger.warn("Failed to check whether this server is running the latest configuration");
        }
    }

    @Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
    public void schedule() {
        globalEventLoop.schedule(this::check);
    }

    public boolean check() {
        if (identifierService.version == null) {
            return true;
        }
        if (!isLatest) {
            // this server is not the latest, no need to check
            return false;
        }
        Optional<Boolean> result = redisTemplate.execute(new VersionChecker(versionKey, identifierService.version));
        if (result != null && result.isPresent()) {
            isLatest = result.get();
            if (!isLatest) {
                // no longer the latest
                logger.info("This server is no longer running the latest configuration (instanceId: {}, version: {}), no longer taking part in leader election.", identifierService.instanceId, identifierService.version);
                stopLeaderShipElection();
            }
        }
        return isLatest;
    }

    private void stopLeaderShipElection() {
        if (leaderService.isLeader()) {
            // we are still the leader,
            // release leadership after 25 seconds, such that other replicas can catch up.
            // every replica checks every 20 seconds whether it's still running the latest config
            // by waiting 25 seconds before releasing the leadership, we are sure other replicas have noticed
            // they are not running the latest config. Otherwise other replicas could immediately take over the leadership.
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(25_000);
                } catch (InterruptedException ignored) {
                }
                lockRegistryLeaderInitiator.destroy();
            });
            thread.start();
        } else {
            lockRegistryLeaderInitiator.destroy();
        }
    }

    private static class VersionChecker implements SessionCallback<Optional<Boolean>> {

        private final String key;
        private final Long version;

        public VersionChecker(String key, Long version) {
            this.key = key;
            this.version = version;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <K, V> Optional<Boolean> execute(@Nonnull RedisOperations<K, V> operations) throws DataAccessException {
            return internalExecute((RedisOperations<String, Long>) operations);
        }

        private Optional<Boolean> internalExecute(@Nonnull RedisOperations<String, Long> operations) throws DataAccessException {
            BoundValueOperations<String, Long> ops = operations.boundValueOps(key);

            operations.watch(key);

            Long redisVersion = ops.get();
            if (redisVersion == null || version > redisVersion) {
                operations.multi();
                ops.set(version);
                var res = operations.exec();
                if (!res.isEmpty()) {
                    // updated key, we are now the latest version
                    return Optional.of(true);
                }
                // unable to update key
                return Optional.empty();
            } else if (version.equals(redisVersion)) {
                // version in Redis is already the latest version
                return Optional.of(true);
            }
            return Optional.of(false);
        }
    }

}
