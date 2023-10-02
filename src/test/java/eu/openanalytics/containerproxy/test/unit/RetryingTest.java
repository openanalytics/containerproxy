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
package eu.openanalytics.containerproxy.test.unit;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.test.proxy.PropertyOverrideContextInitializer;
import eu.openanalytics.containerproxy.test.proxy.TestProxyService;
import eu.openanalytics.containerproxy.util.Retrying;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(classes = {TestProxyService.TestConfiguration.class, ContainerProxyApplication.class})
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
public class RetryingTest {

    @Test
    public void testNumberOfAttempts() {
        Assertions.assertEquals(Retrying.numberOfAttempts(3_000), 11);
        Assertions.assertEquals(Retrying.numberOfAttempts(10_000), 15);
        Assertions.assertEquals(Retrying.numberOfAttempts(60_000), 40); // compared to 30 when not using the faster checks
    }

    @Test
    public void testFastDelay() {
        Instant start = Instant.now();
        AtomicInteger called = new AtomicInteger(0);
        Retrying.retry((i, m) -> {
            called.incrementAndGet();
            return false;
        }, 3_000);
        long time = Duration.between(start, Instant.now()).toMillis();
        Assertions.assertTrue(time > 3_000);
        Assertions.assertTrue(time < 5_000);
        Assertions.assertEquals(11, called.get());
    }

    @Test
    public void testSlowDelay() {
        Instant start = Instant.now();
        AtomicInteger called = new AtomicInteger(0);
        Retrying.retry((i, m) -> {
            called.incrementAndGet();
            return false;
        }, 10_000);
        long time = Duration.between(start, Instant.now()).toMillis();
        Assertions.assertTrue(time > 10_000);
        Assertions.assertTrue(time < 12_000);
        Assertions.assertEquals(15, called.get());
    }

}
