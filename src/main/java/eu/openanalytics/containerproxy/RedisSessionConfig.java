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
package eu.openanalytics.containerproxy;

import eu.openanalytics.containerproxy.service.IdentifierService;
import org.springframework.boot.actuate.data.redis.RedisHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.session.RedisSessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.ConfigureNotifyKeyspaceEventsAction;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.RedisIndexedHttpSessionConfiguration;

import java.util.Objects;

@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@Import(RedisAutoConfiguration.class)
@EnableConfigurationProperties(RedisSessionProperties.class)
public class RedisSessionConfig extends RedisIndexedHttpSessionConfiguration {

    private final String redisNamespace;
    private final Environment environment;

    public RedisSessionConfig(IdentifierService identifierService, Environment environment, RedisSessionProperties redisSessionProperties) {
        this.environment = environment;
        if (identifierService.realmId != null) {
            redisNamespace = String.format("shinyproxy__%s__%s", identifierService.realmId, RedisIndexedSessionRepository.DEFAULT_NAMESPACE);
        } else {
            redisNamespace = String.format("shinyproxy__%s", RedisIndexedSessionRepository.DEFAULT_NAMESPACE);
        }
        ConfigureRedisAction configureRedisAction = switch (redisSessionProperties.getConfigureAction()) {
            case NOTIFY_KEYSPACE_EVENTS -> new ConfigureNotifyKeyspaceEventsAction();
            case NONE -> ConfigureRedisAction.NO_OP;
        };
        setConfigureRedisAction(configureRedisAction);
    }

    @Override
    public String getRedisNamespace() {
        return redisNamespace;
    }

    @Bean
    public HealthIndicator redisSessionHealthIndicator(RedisConnectionFactory rdeRedisConnectionFactory) {
        if (Objects.equals(environment.getProperty("spring.session.store-type"), "redis")) {
            // if we are using redis for session -> use a proper health check for redis
            return new RedisHealthIndicator(rdeRedisConnectionFactory);
        } else {
            // not using redis for session -> just pretend it's always online
            return new HealthIndicator() {

                @Override
                public Health getHealth(boolean includeDetails) {
                    return Health.up().build();
                }

                @Override
                public Health health() {
                    return Health.up().build();
                }
            };
        }
    }

}
