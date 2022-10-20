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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IActiveProxies;
import eu.openanalytics.containerproxy.model.store.IHeartbeatStore;
import eu.openanalytics.containerproxy.model.store.redis.RedisActiveProxies;
import eu.openanalytics.containerproxy.model.store.redis.RedisHeartbeatStore;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.leader.redis.RedisLeaderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.leader.event.DefaultLeaderEventPublisher;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.integration.support.locks.ExpirableLockRegistry;

import javax.inject.Inject;

@Configuration
@ConditionalOnProperty(name = "proxy.store-mode", havingValue = "redis")
public class RedisStoreConfiguration {

    @Inject
    private RedisConnectionFactory connectionFactory;

    @Inject
    private IdentifierService identifierService;

    // Store beans

    @Bean
    public IActiveProxies activeProxies() {
        return new RedisActiveProxies();
    }

    @Bean
    public IHeartbeatStore heartbeatStore() {
        return new RedisHeartbeatStore();
    }

    @Bean
    public RedisLeaderService leaderService() {
        return new RedisLeaderService();
    }

    // Beans used internally by Redis store

    @Bean
    public ExpirableLockRegistry expirableLockRegistry() {
        return new RedisLockRegistry(connectionFactory, "shinyproxy__"  + identifierService.realmId + "__locks");
    }

    @Bean
    public RedisTemplate<String, Proxy> proxyRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Proxy> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        Jackson2JsonRedisSerializer<Proxy> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Proxy.class);
        ObjectMapper om = new ObjectMapper();
        om.setConfig(om.getSerializationConfig().withView(Views.Internal.class));
        jackson2JsonRedisSerializer.setObjectMapper(om);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        return template;
    }

    @Bean
    public RedisTemplate<String, Long> heartbeatsRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericToStringSerializer<>(Long.class));
        template.setHashValueSerializer(new GenericToStringSerializer<>(Long.class));

        return template;
    }

    @Bean
    public LockRegistryLeaderInitiator leaderInitiator(ApplicationEventPublisher applicationEventPublisher) {
        LockRegistryLeaderInitiator initiator = new LockRegistryLeaderInitiator(expirableLockRegistry(), leaderService());
        initiator.setLeaderEventPublisher(new DefaultLeaderEventPublisher(applicationEventPublisher));
        return initiator;
    }

}
