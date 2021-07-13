/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.session.RedisSessionProperties;
import org.springframework.boot.autoconfigure.session.SessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.session.data.redis.config.ConfigureNotifyKeyspaceEventsAction;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration;

import javax.inject.Inject;
import java.time.Duration;

@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisSessionProperties.class)
public class RedisSessionConfig extends RedisHttpSessionConfiguration {

    private String redisNamespace;

    @Inject
    private IdentifierService identifierService;

    @Bean
    @ConditionalOnMissingBean
    public ConfigureRedisAction configureRedisAction(RedisSessionProperties redisSessionProperties) {
        switch (redisSessionProperties.getConfigureAction()) {
            case NOTIFY_KEYSPACE_EVENTS:
                return new ConfigureNotifyKeyspaceEventsAction();
            case NONE:
                return ConfigureRedisAction.NO_OP;
        }
        throw new IllegalStateException(
                "Unsupported redis configure action '" + redisSessionProperties.getConfigureAction() + "'.");

    }

    @Autowired
    public void customize(SessionProperties sessionProperties, RedisSessionProperties redisSessionProperties) {
        Duration timeout = sessionProperties.getTimeout();
        if (timeout != null) {
            setMaxInactiveIntervalInSeconds((int) timeout.getSeconds());
        }
        setFlushMode(redisSessionProperties.getFlushMode());
        setSaveMode(redisSessionProperties.getSaveMode());
        setCleanupCron(redisSessionProperties.getCleanupCron());

        if (identifierService.realmId != null) {
            redisNamespace = String.format("shinyproxy__%s__%s", identifierService.realmId, redisSessionProperties.getNamespace());
        } else {
            redisNamespace = String.format("shinyproxy__%s", redisSessionProperties.getNamespace());
        }

        setRedisNamespace(redisNamespace);
    }

    public String getNamespace() {
        return redisNamespace;
    }

}
