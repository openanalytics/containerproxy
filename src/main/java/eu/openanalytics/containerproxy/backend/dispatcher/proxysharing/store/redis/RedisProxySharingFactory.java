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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.redis;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.Seat;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.IProxySharingStoreFactory;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.service.IdentifierService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "proxy.store-mode", havingValue = "Redis")
public class RedisProxySharingFactory implements IProxySharingStoreFactory {

    private final RedisTemplate<String, String> unClaimSeatIdsTemplate;
    private final RedisTemplate<String, Seat> seatsTemplate;
    private final RedisTemplate<String, DelegateProxy> delegateProxyRedisTemplate;
    private final IdentifierService identifierService;

    public RedisProxySharingFactory(RedisTemplate<String, String> unClaimSeatIdsTemplate,
                                    RedisTemplate<String, Seat> seatsTemplate,
                                    RedisTemplate<String, DelegateProxy> delegateProxyRedisTemplate,
                                    IdentifierService identifierService) {
        this.unClaimSeatIdsTemplate = unClaimSeatIdsTemplate;
        this.seatsTemplate = seatsTemplate;
        this.delegateProxyRedisTemplate = delegateProxyRedisTemplate;
        this.identifierService = identifierService;
    }

    @Override
    public ISeatStore createSeatStore(String specId) {
        String unclaimedSeatIdsKey = "shinyproxy_" + identifierService.realmId + "__unclaimed_seat_ids_" + specId;
        return new RedisSeatStore(seatsTemplate.boundHashOps("shinyproxy_" + identifierService.realmId + "__seats_" + specId),
            unClaimSeatIdsTemplate.boundSetOps(unclaimedSeatIdsKey),
            unClaimSeatIdsTemplate,
            unclaimedSeatIdsKey);
    }

    @Override
    public IDelegateProxyStore createDelegateProxyStore(String specId) {
        return new RedisDelegateProxyStore(delegateProxyRedisTemplate.boundHashOps("shinyproxy_" + identifierService.realmId + "__delegate_proxies_" + specId));
    }

}
