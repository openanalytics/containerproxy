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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.memory;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.IProxySharingStoreFactory;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "proxy.store-mode", havingValue = "None", matchIfMissing = true)
public class MemoryProxySharingFactory implements IProxySharingStoreFactory {

    @Override
    public ISeatStore createSeatStore(String specId) {
        return new MemorySeatStore();
    }

    @Override
    public IDelegateProxyStore createDelegateProxyStore(String specId) {
        return new MemoryDelegateProxyStore();
    }

}
