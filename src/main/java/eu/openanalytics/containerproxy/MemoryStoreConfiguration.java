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

import eu.openanalytics.containerproxy.model.store.IActiveProxies;
import eu.openanalytics.containerproxy.model.store.IHeartbeatStore;
import eu.openanalytics.containerproxy.model.store.memory.MemoryActiveProxies;
import eu.openanalytics.containerproxy.model.store.memory.MemoryHeartbeatStore;
import eu.openanalytics.containerproxy.service.leader.memory.MemoryLeaderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "proxy.store-mode", havingValue = "none", matchIfMissing=true)
public class MemoryStoreConfiguration {

    @Bean
    public IActiveProxies activeProxies() {
        return new MemoryActiveProxies();
    }

    @Bean
    public IHeartbeatStore heartbeatStore() {
        return new MemoryHeartbeatStore();
    }

    @Bean
    public MemoryLeaderService leaderService() {
        return new MemoryLeaderService();
    }

}
