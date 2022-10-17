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
package eu.openanalytics.containerproxy.model.runtime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ProxyStartupLog {

    private final StartupStep createProxy = new StartupStep();
    private final Map<Integer, StartupStep> scheduleContainer = new HashMap<>();
    private final Map<Integer, StartupStep> pullImage = new HashMap<>();
    private final Map<Integer, StartupStep> startContainer = new HashMap<>();
    private final Map<Integer, StartupStep> startApplication = new HashMap<>();

    public StartupStep getCreateProxy() {
        return createProxy;
    }

    public StartupStep getScheduleContainer(Integer containerIdx) {
        Objects.requireNonNull(containerIdx, "ContainerIdx may not be null");
        return scheduleContainer.computeIfAbsent(containerIdx, s -> new StartupStep());
    }

    public StartupStep getPullImage(Integer containerIdx) {
        Objects.requireNonNull(containerIdx, "ContainerIdx may not be null");
        return pullImage.computeIfAbsent(containerIdx, s -> new StartupStep());
    }

    public StartupStep getStartContainer(Integer containerIdx) {
        Objects.requireNonNull(containerIdx, "ContainerIdx may not be null");
        return startContainer.computeIfAbsent(containerIdx, s -> new StartupStep());
    }

    public StartupStep getStartApplication(Integer containerIdx) {
        Objects.requireNonNull(containerIdx, "ContainerIdx may not be null");
        return startApplication.computeIfAbsent(containerIdx, s -> new StartupStep());
    }

    public Map<Integer, StartupStep> getStartContainer() {
        return startContainer;
    }

    public Map<Integer, StartupStep> getStartApplication() {
        return startApplication;
    }

    public Map<Integer, StartupStep> getPullImage() {
        return pullImage;
    }

    public Map<Integer, StartupStep> getScheduleContainer() {
        return scheduleContainer;
    }

    public static class StartupStep {

        private LocalDateTime startTime = null;
        private LocalDateTime endTime = null;
        private StartupStepState state = StartupStepState.NOT_EXECUTED;

        public void stepStarted() {
            if (state != StartupStepState.NOT_EXECUTED) {
                throw new IllegalStateException("Cannot start step if it's already started!");
            }
            startTime = LocalDateTime.now();
            state = StartupStepState.STARTED;
        }

        public void stepSucceeded() {
            if (state != StartupStepState.STARTED) {
                throw new IllegalStateException("Cannot finish (with success) step if it is not yet started or already completed");
            }
            endTime = LocalDateTime.now();
            state = StartupStepState.SUCCESS;
        }

        public void stepSucceeded(LocalDateTime startTime, LocalDateTime endTime) {
            if (state != StartupStepState.NOT_EXECUTED || startTime == null || endTime == null) {
                throw new IllegalStateException("Cannot start step if it's already started!");
            }
            this.startTime = startTime;
            this.endTime = endTime;
            state = StartupStepState.SUCCESS;
        }

        public void stepFailed() {
            if (state != StartupStepState.STARTED) {
                return; // step was never started -> don't record endTime
            }
            endTime = LocalDateTime.now();
            state = StartupStepState.FAILURE;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public Optional<Duration> getStepDuration() {
            if (endTime == null || startTime == null) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(startTime, endTime));
        }

        public StartupStepState getState() {
            return state;
        }

    }

    public enum StartupStepState {
        NOT_EXECUTED,
        STARTED,
        SUCCESS,
        FAILURE
    }

}
