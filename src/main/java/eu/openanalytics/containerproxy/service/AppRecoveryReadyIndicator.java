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
package eu.openanalytics.containerproxy.service;

import javax.inject.Inject;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Indicates whether the recovery of existing apps is completed.
 * While the recovery is happening, the application may not be used.
 * Therefore the readiness probes should report that the application is down.
 */
@Component
public class AppRecoveryReadyIndicator implements HealthIndicator {

	@Inject
	public AppRecoveryService appRecoveryService;

    @Override
    public Health health() {
    	if (appRecoveryService.isReady()) {
    		return Health.up().build();
    	}
		return Health.down().build();
    }
}
