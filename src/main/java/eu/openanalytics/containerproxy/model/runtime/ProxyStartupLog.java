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
package eu.openanalytics.containerproxy.model.runtime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Value
@EqualsAndHashCode
public class ProxyStartupLog {

    StartupStep createProxy;

    Map<Integer, StartupStep> pullImage;

    Map<Integer, StartupStep> scheduleContainer;

    Map<Integer, StartupStep> startContainer;

    StartupStep startApplication;

    public ProxyStartupLog(@JsonProperty("createProxy") StartupStep createProxy,
                           @JsonProperty("pullImage") Map<Integer, StartupStep> pullImage,
                           @JsonProperty("scheduleContainer") Map<Integer, StartupStep> scheduleContainer,
                           @JsonProperty("startContainer") Map<Integer, StartupStep>  startContainer,
                           @JsonProperty("startApplication") StartupStep startApplication) {
        this.createProxy = createProxy;
        this.pullImage = pullImage;
        this.scheduleContainer = scheduleContainer;
        this.startContainer = startContainer;
        this.startApplication = startApplication;
    }

    public StartupStep getCreateProxy() {
        return createProxy;
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder();
        res.append("CreateProxy: ").append(createProxy).append("\n");
        res.append("PullImage: \n");
        for (Map.Entry<Integer, StartupStep> m : pullImage.entrySet()) {
            res.append("\t Container ").append(m.getKey()).append(": ").append(m.getValue()).append("\n");
        }
        res.append("ScheduleContainer: \n");
        for (Map.Entry<Integer, StartupStep> m : scheduleContainer.entrySet()) {
            res.append("\t Container ").append(m.getKey()).append(": ").append(m.getValue()).append("\n");
        }
        res.append("StartContainer: \n");
        for (Map.Entry<Integer, StartupStep> m : startContainer.entrySet()) {
            res.append("\t Container ").append(m.getKey()).append(": ").append(m.getValue()).append("\n");
        }
        res.append("StartApplication: ").append(startApplication).append("\n");

        return res.toString();
    }

    public static class ProxyStartupLogBuilder {

        private StartupStep createProxy = new StartupStep();

        private final Map<Integer, StartupStep> pullImage = new HashMap<>();

        private final Map<Integer, StartupStep> startContainer =  new HashMap<>();

        private final Map<Integer, StartupStep> scheduleContainer =  new HashMap<>();

        private StartupStep startApplication = null;

        public void pullingImage(Integer containerIdx) {
            if (pullImage.containsKey(containerIdx)) {
                throw new IllegalStateException(String.format("StartupLog already contains an entry for container %s and action pullingImage", containerIdx));
            }
            StartupStep step = new StartupStep();
            pullImage.put(containerIdx, step);
        }

        public void imagePulled(Integer containerIdx) {
            if (!pullImage.containsKey(containerIdx)) {
                throw new IllegalStateException(String.format("StartupLog does not have an entry for container %s and action imagePulled", containerIdx));
            }
            StartupStep old = pullImage.get(containerIdx);
            pullImage.put(containerIdx, new StartupStep(old.startTime, LocalDateTime.now()));
        }

        public void imagePulled(int containerIdx, LocalDateTime start, LocalDateTime end) {
            if (pullImage.containsKey(containerIdx)) {
                throw new IllegalStateException(String.format("StartupLog already contains an entry for container %s and action imagePulled", containerIdx));
            }
            pullImage.put(containerIdx, new StartupStep(start, end));
        }

        public void containerScheduled(int containerIdx, LocalDateTime start, LocalDateTime end) {
            if (scheduleContainer.containsKey(containerIdx)) {
                throw new IllegalStateException(String.format("StartupLog already contains an entry for container %s and action containerScheduled", containerIdx));
            }
            scheduleContainer.put(containerIdx, new StartupStep(start, end));
        }

        public void startingContainer(Integer containerIdx) {
            if (startContainer.containsKey(containerIdx)) {
                throw new IllegalStateException(String.format("StartupLog does not have an entry for container %s and action startingContainer", containerIdx));
            }
            StartupStep step = new StartupStep();
            startContainer.put(containerIdx, step);
        }

        public void containerStarted(Integer containerIdx) {
            if (!startContainer.containsKey(containerIdx)) {
                throw new IllegalStateException(String.format("StartupLog does not have an entry for container %s and action containerStarted", containerIdx));
            }
            StartupStep old = startContainer.get(containerIdx);
            startContainer.put(containerIdx, new StartupStep(old.startTime, LocalDateTime.now()));
        }

        public void startingApplication() {
            if (startApplication != null) {
                throw new IllegalStateException("StartupLog already contains an entry for action startingApplication");
            }
            startApplication= new StartupStep();
        }

        public void applicationStarted() {
            if (startApplication == null) {
                throw new IllegalStateException("StartupLog does not have an entry for action startingApplication");
            }
            startApplication = new StartupStep(startApplication.startTime, LocalDateTime.now());
        }

        public ProxyStartupLog succeeded() {
            createProxy = new StartupStep(createProxy.startTime, LocalDateTime.now());
            return new ProxyStartupLog(createProxy, pullImage, scheduleContainer, startContainer, startApplication);
        }

    }

    @Value
    @EqualsAndHashCode
    public static class StartupStep {

        LocalDateTime startTime;
        LocalDateTime endTime;

        public StartupStep(@JsonProperty("startTime") LocalDateTime startTime,
                           @JsonProperty("endTime") LocalDateTime endTime) {
            Objects.requireNonNull(startTime, "StartTime may not be null");
            Objects.requireNonNull(endTime, "EndTime may not be null");
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public StartupStep() {
            this.startTime = LocalDateTime.now();
            this.endTime = null;
        }

        public String toString() {
            return "Start: " + startTime + " end: " + endTime + " duration: " + getStepDuration().map(Duration::toMillis).orElse(0L) + "ms";
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        @JsonIgnore
        public Optional<Duration> getStepDuration() {
            if (endTime == null || startTime == null) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(startTime, endTime));
        }

    }

}
