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
package eu.openanalytics.containerproxy.util;

public class Retrying {

    @FunctionalInterface
    public interface Attempt {
        boolean attempt(int currentAttempt, int maxAttempts);
    }

    public static boolean retry(Attempt job, int maxDelay) {
        return retry(job, maxDelay, false);
    }

    public static boolean retry(Attempt job, int maxDelay, boolean retryOnException) {
        boolean retVal = false;
        RuntimeException exception = null;
        int maxAttempts = numberOfAttempts(maxDelay);
        for (int currentAttempt = 0; currentAttempt < maxAttempts; currentAttempt++) {
            delay(currentAttempt); // delay here so that we don't delay for the last iteration
            try {
                if (job.attempt(currentAttempt, maxAttempts)) {
                    retVal = true;
                    exception = null;
                    break;
                }
            } catch (RuntimeException e) {
                if (retryOnException) exception = e;
                else throw e;
            }
        }
        if (exception == null) return retVal;
        else throw exception;
    }

    public static void delay(Integer attempt) {
        try {
            if (attempt == 0) {
            } else if (attempt <= 5) {
                Thread.sleep(200);
            } else if (attempt <= 10) {
                Thread.sleep(400);
            } else {
                Thread.sleep(2_000);
            }
        } catch (InterruptedException ignore) {
        }
    }

    public static int numberOfAttempts(Integer maxDelay) {
        if (maxDelay < 2_000) {
            throw new IllegalArgumentException("The maximum delay should at least be 2000ms");
        }
        // it takes 11 attempts to have a delay of 3 000ms
        return (int) Math.ceil((maxDelay - 3_000) / 2_000.0) + 11;
    }

}
