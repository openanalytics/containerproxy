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
package eu.openanalytics.containerproxy.test.helpers;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingScaler;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;

public class TestProxySharingScaler extends ProxySharingScaler {

    private boolean cleanup = true;

    public TestProxySharingScaler(ISeatStore seatStore, ProxySpec proxySpec, IDelegateProxyStore delegateProxyStore) {
        super(seatStore, proxySpec, delegateProxyStore);
    }

    public void disableCleanup() {
        cleanup = false;
    }

    public void enableCleanup() {
        cleanup = true;
    }

    @Override
    protected void cleanup() {
        if (cleanup) {
            super.cleanup();
        }
    }

    public IDelegateProxyStore getDelegateProxyStore() {
        return delegateProxyStore;
    }

    public ISeatStore getSeatStore() {
        return seatStore;
    }

    public ProxySharingScaler.ReconcileStatus getLastReconcileStatus() {
        return lastReconcileStatus;
    }

}
