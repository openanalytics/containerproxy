/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
package eu.openanalytics.containerproxy.api;

import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.backend.dispatcher.ProxyDispatcherService;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingScaler;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingSpecExtension;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.inject.Inject;

@RestController
public class AdminController extends BaseController {

    @Inject
    private ProxyDispatcherService proxyDispatcherService;

    @Inject
    private ProxyService proxyService;

    @Inject
    private UserService userService;

    @Inject
    private Environment environment;

    @RequestMapping(value = "/admin/app/{specId}/scale", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> scaleApp(@PathVariable String specId, @RequestBody ScaleRequest request) {
        // 1. Authentication Check
        if (!userService.isAdmin()) {
            return ApiResponse.failForbidden();
        }

        // 2. Get Spec & Check Configuration
        ProxySpec spec = proxyService.getUserSpec(specId);
        if (spec == null) {
            return ApiResponse.fail("App not found");
        }

        ProxySharingSpecExtension ext = spec.getSpecExtension(ProxySharingSpecExtension.class);
        if (ext == null || !ext.isAllowDynamicScaling()) {
            return ApiResponse.fail("Dynamic scaling not allowed for this app");
        }

        // 3. Get Scaler
        ProxySharingScaler scaler = proxyDispatcherService.getProxySharingScaler(specId);
        if (scaler == null) {
            return ApiResponse.fail("Scaler not found");
        }

        // 4. Validate and Apply Scale
        Integer seats = request.getSeats();
        if (seats == null) {
            // Reset to config default
            scaler.setMinimumSeatsAvailable(null);
        } else {
            int currentSeats = scaler.getMinimumSeatsAvailable();
            int delta = seats - currentSeats;

            // Reductions are always allowed (even if currently over-allocated)
            if (delta < 0) {
                scaler.setMinimumSeatsAvailable(seats);
            } else if (delta > 0) {
                // Validate per-app max instances
                Integer appMaxInstances = spec.getMaxTotalInstances();
                if (appMaxInstances > -1 && seats > appMaxInstances) {
                    return ApiResponse.fail(
                        "Requested seats (" + seats + ") exceed per-app maximum instances limit (" + appMaxInstances + ")"
                    );
                }

                // Validate global capacity
                Integer globalMaxInstances = environment.getProperty("proxy.max-total-instances", Integer.class, -1);
                if (globalMaxInstances > -1) {
                    int totalCurrentMinSeats = calculateTotalMinSeatsAcrossAllApps();
                    int remainingCapacity = globalMaxInstances - totalCurrentMinSeats;

                    if (delta > remainingCapacity) {
                        return ApiResponse.fail(
                            "Requested increase (" + delta + " seats) exceeds remaining global capacity (" + remainingCapacity + ")"
                        );
                    }
                }

                scaler.setMinimumSeatsAvailable(seats);
            }
        }

        return ApiResponse.success();
    }

    private int calculateTotalMinSeatsAcrossAllApps() {
        return proxyDispatcherService.getAllProxySharingScalers().stream()
            .mapToInt(ProxySharingScaler::getMinimumSeatsAvailable)
            .sum();
    }

    public static class ScaleRequest {
        @Schema(description = "Minimum number of seats to maintain available", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer seats;

        public Integer getSeats() {
            return seats;
        }

        public void setSeats(Integer seats) {
            this.seats = seats;
        }
    }
}
