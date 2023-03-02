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
package eu.openanalytics.containerproxy.test.proxy;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.portallocator.IPortAllocator;
import eu.openanalytics.containerproxy.service.portallocator.memory.MemoryPortAllocator;
import eu.openanalytics.containerproxy.service.portallocator.redis.RedisPortAllocator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;


/**
 * These tests require redis to be running...
 */
@SpringBootTest(classes = {ContainerProxyApplication.class})
@ContextConfiguration(initializers = TestIntegrationPortAllocator.PropertyOverride.class)
@ActiveProfiles("redis-integration")
@TestInstance(PER_CLASS)
public class TestIntegrationPortAllocator {

    @Inject
    private RedisTemplate<String, RedisPortAllocator.PortList> portTemplate;

    @Inject
    private IdentifierService identifierService;

    private Stream<Arguments> parameters() {
        return Stream.of(
                Arguments.of(new RedisPortAllocator(portTemplate, identifierService)),
                Arguments.of(new MemoryPortAllocator())
        );
    }

    @BeforeEach
    public void beforeTest() {
        portTemplate.delete("shinyproxy_" + identifierService.realmId + "__ports");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testConcurrentAllocate(IPortAllocator portAllocator) throws InterruptedException {
        HashSet<Integer> portsThread1 = new HashSet<>();
        HashSet<Integer> portsThread2 = new HashSet<>();
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                portsThread1.add(portAllocator.allocate(100, 1000, "thread1"));
            }
        });
        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                portsThread2.add(portAllocator.allocate(100, 1000, "thread2"));
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        Assertions.assertEquals(100, portsThread1.size());
        Assertions.assertEquals(100, portsThread2.size());
        Assertions.assertEquals(portAllocator.getOwnedPorts("thread1"), portsThread1);
        Assertions.assertEquals(portAllocator.getOwnedPorts("thread2"), portsThread2);

        List<Integer> allAllocatedPorts = Stream.concat(portsThread1.stream(), portsThread2.stream())
                .sorted()
                .collect(Collectors.toList());

        List<Integer> expectedPorts = IntStream.range(100, 300).boxed().collect(Collectors.toList());

        Assertions.assertEquals(expectedPorts, allAllocatedPorts);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testRelease(IPortAllocator portAllocator) {
        // allocate some ports
        for (int i = 0; i < 5; i++) {
            portAllocator.allocate(100, 1000, "owner1");
        }
        for (int i = 0; i < 5; i++) {
            portAllocator.allocate(100, 1000, "owner2");
        }
        portAllocator.allocate(100, 1000, "owner1");
        portAllocator.allocate(100, 1000, "owner2");
        for (int i = 0; i < 5; i++) {
            portAllocator.allocate(100, 1000, "owner1");
        }
        HashSet<Integer> expectedOwner1 = new HashSet<>();
        HashSet<Integer> expectedOwner2 = new HashSet<>();
        expectedOwner1.add(100);
        expectedOwner1.add(101);
        expectedOwner1.add(102);
        expectedOwner1.add(103);
        expectedOwner1.add(104);
        expectedOwner2.add(105);
        expectedOwner2.add(106);
        expectedOwner2.add(107);
        expectedOwner2.add(108);
        expectedOwner2.add(109);
        expectedOwner1.add(110);
        expectedOwner2.add(111);
        expectedOwner1.add(112);
        expectedOwner1.add(113);
        expectedOwner1.add(114);
        expectedOwner1.add(115);
        expectedOwner1.add(116);
        Assertions.assertEquals(expectedOwner1, portAllocator.getOwnedPorts("owner1"));
        Assertions.assertEquals(expectedOwner2, portAllocator.getOwnedPorts("owner2"));

        portAllocator.release("owner2");
        Assertions.assertEquals(expectedOwner1, portAllocator.getOwnedPorts("owner1"));
        Assertions.assertEquals(new HashSet<>(), portAllocator.getOwnedPorts("owner2"));
        // re-allocate released ports
        for (int i = 0; i < 10; i++) {
            portAllocator.allocate(100, 1000, "owner3");
        }
        HashSet<Integer> expectedOwner3 = new HashSet<>();
        expectedOwner3.add(105);
        expectedOwner3.add(106);
        expectedOwner3.add(107);
        expectedOwner3.add(108);
        expectedOwner3.add(109);
        expectedOwner3.add(111);
        expectedOwner3.add(117);
        expectedOwner3.add(118);
        expectedOwner3.add(119);
        expectedOwner3.add(120);
        Assertions.assertEquals(expectedOwner3, portAllocator.getOwnedPorts("owner3"));

        portAllocator.release("owner2"); // should just do nothing
        Assertions.assertEquals(expectedOwner1, portAllocator.getOwnedPorts("owner1"));
        Assertions.assertEquals(expectedOwner3, portAllocator.getOwnedPorts("owner3"));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testReleaseConcurrency(IPortAllocator portAllocator) throws InterruptedException {
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                portAllocator.allocate(100, 1000, "owner1_1");
            }
            portAllocator.release("owner1_1");
            for (int i = 0; i < 5; i++) {
                portAllocator.allocate(100, 1000, "owner1_2");
            }
        });
        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                portAllocator.allocate(100, 1000, "owner2_1");
            }
            portAllocator.release("owner2_1");
            for (int i = 0; i < 5; i++) {
                portAllocator.allocate(100, 1000, "owner2_2");
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        Assertions.assertEquals(new HashSet<>(), portAllocator.getOwnedPorts("owner1_1"));
        Assertions.assertEquals(new HashSet<>(), portAllocator.getOwnedPorts("owner2_1"));
        Assertions.assertEquals(5, portAllocator.getOwnedPorts("owner1_2").size());
        Assertions.assertEquals(5, portAllocator.getOwnedPorts("owner2_2").size());
        List<Integer> allAllocatedPorts = Stream.concat(portAllocator.getOwnedPorts("owner1_2").stream(),
                        portAllocator.getOwnedPorts("owner2_2").stream())
                .sorted()
                .collect(Collectors.toList());

        List<Integer> expectedPorts = IntStream.range(100, 110).boxed().collect(Collectors.toList());
        Assertions.assertEquals(expectedPorts, allAllocatedPorts);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testNoPortAvailable(IPortAllocator portAllocator) {
        for (int i = 0; i < 5; i++) {
            portAllocator.allocate(100, 104, "owner1_1");
        }
        Assertions.assertThrows(ContainerProxyException.class, () -> {
            portAllocator.allocate(100, 104, "owner1_1");
        }, "Cannot create container: all allocated ports are currently in use. Please try again later or contact an administrator.");
    }

    public static class PropertyOverride extends PropertyOverrideContextInitializer {

        @Override
        public void initialize(@Nonnull ConfigurableApplicationContext configurableApplicationContext) {
            super.initialize(configurableApplicationContext);

            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
                    "proxy.realm-id=" + UUID.randomUUID());

        }

    }

}
