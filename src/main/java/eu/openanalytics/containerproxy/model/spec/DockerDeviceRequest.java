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
package eu.openanalytics.containerproxy.model.spec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DockerDeviceRequest {

    private String driver;
    private Integer count;
    private List<String> deviceIds;
    private List<List<String>> capabilities;
    private Map<String, String> options;

    public DockerDeviceRequest(String driver, Integer count, List<String> deviceIds, List<List<String>> capabilities, Map<String, String> options) {
        this.driver = driver;
        this.count = count;
        this.deviceIds = deviceIds;
        this.capabilities = capabilities;
        this.options = options;
    }

    public Optional<String> getDriver() {
        return Optional.ofNullable(driver);
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public Optional<Integer> getCount() {
        return Optional.ofNullable(count);
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Optional<List<String>> getDeviceIds() {
        return Optional.ofNullable(deviceIds);
    }

    public void setDeviceIds(List<String> deviceIds) {
        this.deviceIds = deviceIds;
    }

    public Optional<List<List<String>>> getCapabilities() {
        return Optional.ofNullable(capabilities);
    }

    public void setCapabilities(List<List<String>> capabilities) {
        this.capabilities = capabilities;
    }

    public Optional<Map<String, String>> getOptions() {
        return Optional.ofNullable(options);
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

}
