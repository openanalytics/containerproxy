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
package eu.openanalytics.containerproxy.util;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Retrying {

    private static final Logger log = LoggerFactory.getLogger(Retrying.class);

    public static boolean retry(Attempt job, int maxDelay) {
        return retry(job, maxDelay, null, -1);
    }

    public static boolean retry(Attempt job, int maxDelay, String logMessage, int logAfterAttempts) {
        return retry(job, maxDelay, logMessage, logAfterAttempts, null, null);
    }

    public static boolean retry(Attempt job, int maxDelay, String logMessage, int logAfterAttempts, Proxy proxy, StructuredLogger slog) {
        Exception exception = null;
        int maxAttempts = numberOfAttempts(maxDelay);
        for (int currentAttempt = 0; currentAttempt < maxAttempts; currentAttempt++) {
            try {
                delay(currentAttempt); // delay here so that we don't delay for the last iteration
                Result result = job.attempt(currentAttempt, maxAttempts);
                if (!result.keepGoing) {
                    if (result.success && currentAttempt > logAfterAttempts && logMessage != null) {
                        if (slog != null && proxy != null) {
                            slog.info(proxy, String.format("Ready: %s", logMessage));
                        } else {
                            log.info(String.format("Ready: %s", logMessage));
                        }
                    }
                    return result.success;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                exception = e;
            }
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            if (currentAttempt > logAfterAttempts && logMessage != null) {
                if (slog != null && proxy != null) {
                    slog.info(proxy, String.format("Waiting: %s (%d/%d)", logMessage, currentAttempt, maxAttempts));
                } else {
                    log.info(String.format("Waiting: %s (%d/%d)", logMessage, currentAttempt, maxAttempts));
                }
            }
        }
        if (exception != null) {
            if (slog != null && proxy != null) {
                slog.warn(proxy, exception, String.format("Failed: %s", logMessage));
            } else {
                log.warn(String.format("Failed: %s", logMessage), exception);
            }
        }
        return false;
    }

    public static void delay(Integer attempt) throws InterruptedException {
        if (attempt == 0) {
        } else if (attempt <= 5) {
            Thread.sleep(200);
        } else if (attempt <= 10) {
            Thread.sleep(400);
        } else {
            Thread.sleep(2_000);
        }
    }

    public static int numberOfAttempts(Integer maxDelay) {
        if (maxDelay < 2_000) {
            throw new IllegalArgumentException("The maximum delay should at least be 2000ms");
        }
        // it takes 11 attempts to have a delay of 3 000ms
        return (int) Math.ceil((maxDelay - 3_000) / 2_000.0) + 11;
    }

    @FunctionalInterface
    public interface Attempt {
        Result attempt(int currentAttempt, int maxAttempts) throws Exception;
    }

    public record Result(boolean success, boolean keepGoing) {

        public Result(boolean success) {
            this(success, !success);
        }

    }

    public final static Result SUCCESS = new Result(true, false);
    public final static Result FAILURE = new Result(false, true);

}
