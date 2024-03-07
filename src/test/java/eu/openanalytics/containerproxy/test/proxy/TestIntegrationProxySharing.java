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
package eu.openanalytics.containerproxy.test.proxy;

import com.google.common.base.Throwables;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingScaler;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.Seat;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.SeatIdKey;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxyStatus;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.TargetIdKey;
import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.RedisServer;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import eu.openanalytics.containerproxy.test.helpers.TestHelperException;
import eu.openanalytics.containerproxy.test.helpers.TestProxySharingScaler;
import eu.openanalytics.containerproxy.test.helpers.TestUtil;
import eu.openanalytics.containerproxy.util.Retrying;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder;
import org.mandas.docker.client.exceptions.DockerCertificateException;
import org.mandas.docker.client.exceptions.DockerException;

import javax.json.JsonObject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class TestIntegrationProxySharing {

    private static Stream<Arguments> backends() {
        return Stream.of(
            Arguments.of("docker", Map.of("proxy.container-backend", "docker")),
            Arguments.of("docker-swarm", Map.of("proxy.container-backend", "docker-swarm")),
            Arguments.of("kubernetes", Map.of("proxy.container-backend", "kubernetes"))
        );
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void simpleTest(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-1.yml", properties, true)) {
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                IDelegateProxyStore delegateProxyStore = proxySharingScaler.getDelegateProxyStore();
                ISeatStore seatStore = proxySharingScaler.getSeatStore();
                inst.enableCleanup();
                String id = inst.client.startProxy("myApp");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(proxy.getTargetId());

                // target id should be different from proxy id
                Assertions.assertNotEquals(proxy.getTargetId(), proxy.getId());
                Assertions.assertEquals("/api/route/" + proxy.getTargetId() + "/", proxy.getRuntimeValue(PublicPathKey.inst));
                Assertions.assertEquals(proxy.getTargetId(), proxy.getRuntimeValue(TargetIdKey.inst));

                // check DelegateProxy
                DelegateProxy delegateProxy = delegateProxyStore.getDelegateProxy(proxy.getTargetId());
                Assertions.assertNotNull(delegateProxy);
                Assertions.assertEquals(1, delegateProxy.getSeatIds().size());
                Assertions.assertEquals(proxy.getRuntimeValue(SeatIdKey.inst), delegateProxy.getSeatIds().stream().findFirst().get());

                // check seat
                Seat seat = seatStore.getSeat(proxy.getRuntimeValue(SeatIdKey.inst));
                Assertions.assertEquals(proxy.getRuntimeValue(SeatIdKey.inst), seat.getId());
                Assertions.assertEquals(delegateProxy.getProxy().getId(), seat.getDelegateProxyId());
                Assertions.assertEquals(proxy.getId(), seat.getDelegatingProxyId());

                // should have scaled-up
                waitUntilNoPendingSeats(inst);
                Assertions.assertEquals(1, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());

                // start an additional app
                Instant start = Instant.now();
                String id2 = inst.client.startProxy("myApp");
                Proxy proxy2 = inst.proxyService.getProxy(id2);
                inst.client.testProxyReachable(proxy2.getTargetId());

                // target id should be different from first app
                Assertions.assertNotEquals(proxy2.getTargetId(), proxy.getTargetId());
                // seat id should be different from first app
                Assertions.assertNotEquals(proxy2.getRuntimeValue(SeatIdKey.inst), proxy.getRuntimeValue(SeatIdKey.inst));

                // should have scaled-up
                waitUntilNoPendingSeats(inst);
                Assertions.assertEquals(2, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());

                // stop first app
                inst.client.stopProxy(id);
                Assertions.assertEquals(0, proxySharingScaler.getNumPendingSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(2, proxySharingScaler.getNumUnclaimedSeats());

                // wait until scale-down happened
                waitUntilUnNumberOfUnClaimedSeats(inst, 1);
                Instant stop = Instant.now();
                Assertions.assertEquals(0, proxySharingScaler.getNumPendingSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());
                // scale-down should take at least two minutes
                Assertions.assertTrue(Duration.between(start, stop).toSeconds() > 120);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testSetPublicPathPrefix(String backend, Map<String, String> properties) {
        ProxySharingScaler.setPublicPathPrefix("/my/custom/path/");
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-1.yml", properties, true)) {
                inst.enableCleanup();
                String id = inst.client.startProxy("myApp");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(proxy.getTargetId());

                // target id should be different from proxy id
                Assertions.assertNotEquals(proxy.getTargetId(), proxy.getId());
                Assertions.assertEquals("/my/custom/path/" + proxy.getTargetId() + "/", proxy.getRuntimeValue(PublicPathKey.inst));
                Assertions.assertEquals(proxy.getTargetId(), proxy.getRuntimeValue(TargetIdKey.inst));
                Assertions.assertNotNull(proxy.getRuntimeValue(SeatIdKey.inst));

                inst.client.stopProxy(id);
            }
        } finally {
            ProxySharingScaler.setPublicPathPrefix("/api/route/");
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testNoContainerReUse(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-2.yml", properties, true)) {
                inst.enableCleanup();
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                proxySharingScaler.disableCleanup();
                IDelegateProxyStore delegateProxyStore = proxySharingScaler.getDelegateProxyStore();

                String id = inst.client.startProxy("myApp");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(proxy.getTargetId());

                // target id should be different from proxy id
                Assertions.assertNotEquals(proxy.getTargetId(), proxy.getId());
                Assertions.assertEquals("/api/route/" + proxy.getTargetId() + "/", proxy.getRuntimeValue(PublicPathKey.inst));
                Assertions.assertEquals(proxy.getTargetId(), proxy.getRuntimeValue(TargetIdKey.inst));
                Assertions.assertNotNull(proxy.getRuntimeValue(SeatIdKey.inst));

                // wait for scale up to finish
                waitUntilUnNumberOfUnClaimedSeats(inst, 1);

                List<String> delegateProxyIds = new ArrayList<>(delegateProxyStore.getAllDelegateProxies().stream().map(it -> it.getProxy().getId()).toList());
                Assertions.assertEquals(2, delegateProxyIds.size());
                Assertions.assertTrue(delegateProxyIds.remove(proxy.getTargetId()));
                String existingDelegateProxyId = delegateProxyIds.get(0);

                inst.client.stopProxy(id);

                // proxied stop, DelegateProxy should still be there, but without seats
                Assertions.assertEquals(2, delegateProxyStore.getAllDelegateProxies().size());
                Assertions.assertNotNull(delegateProxyStore.getDelegateProxy(existingDelegateProxyId));
                DelegateProxy delegateProxy = delegateProxyStore.getDelegateProxy(proxy.getTargetId());
                Assertions.assertTrue(delegateProxy.getSeatIds().isEmpty());

                proxySharingScaler.enableCleanup();
                // old proxy should get cleaned up
                waitUntilNumberOfDelegateProxies(inst, 1, 1);
            }
        }
    }

    @Test
    public void testReUseWithMultipleSeats() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-pre-initialization-3.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Spec myApp is invalid: when allow-container-re-use is disabled, seatsPerContainer must be exactly 1", rootCause.getMessage());
    }

    @Test
    public void testDelegateProxyCrashed() throws DockerCertificateException, DockerException, InterruptedException, IOException {
        DockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build();
        try (ContainerSetup containerSetup = new ContainerSetup("docker")) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-1.yml", Map.of("proxy.container-backend", "docker"), true)) {
                inst.enableCleanup();
                String id = inst.client.startProxy("myApp");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(proxy.getTargetId());

                // delete underlying container
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                proxySharingScaler.disableCleanup();
                IDelegateProxyStore delegateProxyStore = proxySharingScaler.getDelegateProxyStore();
                DelegateProxy delegateProxy = delegateProxyStore.getDelegateProxy(proxy.getTargetId());
                Container container = delegateProxy.getProxy().getContainer(0);
                dockerClient.removeContainer(container.getId(), DockerClient.RemoveContainerParam.forceKill());

                // try to access proxy
                Request request = new Request.Builder()
                    .get()
                    .url(inst.client.getBaseUrl() + "/api/route/" + proxy.getTargetId() + "/")
                    .build();

                try (Response response = inst.client.newCall(request).execute()) {
                    Assertions.assertEquals(503, response.code());
                    Assertions.assertEquals("{\"status\":\"fail\",\"data\":\"app_crashed\"}", response.body().string());
                }

                // DelegateProxy should have no seats and marked for removal
                delegateProxy = delegateProxyStore.getDelegateProxy(proxy.getTargetId());
                Assertions.assertEquals(DelegateProxyStatus.ToRemove, delegateProxy.getDelegateProxyStatus());
                Assertions.assertTrue(delegateProxy.getSeatIds().isEmpty());

                // enable cleanup
                proxySharingScaler.enableCleanup();

                // old proxy should get cleaned up
                waitUntilNumberOfDelegateProxies(inst, 1, 1);
                DelegateProxy newDelegateProxy = delegateProxyStore.getAllDelegateProxies().stream().findFirst().get();
                // should be a different proxy, old one should have been removed
                Assertions.assertNotEquals(newDelegateProxy.getProxy().getId(), delegateProxy.getProxy().getId());
                Assertions.assertNotEquals(newDelegateProxy.getProxy().getTargetId(), delegateProxy.getProxy().getTargetId());
                Assertions.assertEquals(1, newDelegateProxy.getSeatIds().size());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testConfigChange(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (RedisServer redisServer = new RedisServer()) {
                // launch an instance and app
                String oldAppId;
                try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-redis-1.yml", properties, true)) {
                    oldAppId = inst.client.startProxy("myApp");
                    Proxy proxy = inst.proxyService.getProxy(oldAppId);
                    inst.client.testProxyReachable(proxy.getTargetId());

                    // target id should be different from proxy id
                    Assertions.assertNotEquals(proxy.getTargetId(), proxy.getId());
                    Assertions.assertEquals("/api/route/" + proxy.getTargetId() + "/", proxy.getRuntimeValue(PublicPathKey.inst));
                    Assertions.assertEquals(proxy.getTargetId(), proxy.getRuntimeValue(TargetIdKey.inst));
                    Assertions.assertNotNull(proxy.getRuntimeValue(SeatIdKey.inst));

                    // wait for scale-up to finish
                    waitUntilNumberOfDelegateProxies(inst, 2, 2);
                }
                // re-start instance with updated app config
                try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-redis-2.yml", properties, true)) {
                    inst.enableCleanup();
                    TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                    proxySharingScaler.disableCleanup();
                    IDelegateProxyStore delegateProxyStore = proxySharingScaler.getDelegateProxyStore();

                    Proxy proxy = inst.proxyService.getProxy(oldAppId);
                    // old app should still exist
                    Assertions.assertNotNull(proxy);
                    // old app should still be reachable
                    inst.client.testProxyReachable(proxy.getTargetId());

                    // wait until app is marked to be removed
                    waitUntilDelegateProxyIsToRemove(proxySharingScaler, proxy.getTargetId());
                    DelegateProxy delegateProxy = delegateProxyStore.getDelegateProxy(proxy.getTargetId());
                    Assertions.assertEquals(DelegateProxyStatus.ToRemove, delegateProxy.getDelegateProxyStatus());
                    Assertions.assertEquals("fcbf978730e85a8517eaa6812ace9bbd08acad1b", delegateProxy.getProxySpecHash());

                    // a DelegateProxy with new config should exist in DelegateProxyStore
                    waitUntilNumberOfDelegateProxies(inst, 3, 1, 0, 2);
                    Optional<DelegateProxy> newDelegateProxy = delegateProxyStore.getAllDelegateProxies().stream()
                        .filter(it -> !it.getProxySpecHash().equals("fcbf978730e85a8517eaa6812ace9bbd08acad1b"))
                        .findFirst();
                    Assertions.assertTrue(newDelegateProxy.isPresent());
                    Assertions.assertEquals(DelegateProxyStatus.Available, newDelegateProxy.get().getDelegateProxyStatus());
                    Assertions.assertEquals("ac5969e6d722a0951143ba125846feeff4491b33", newDelegateProxy.get().getProxySpecHash());

                    // stop running app
                    inst.client.stopProxy(oldAppId);

                    proxySharingScaler.enableCleanup();

                    // proxies with old version should get cleaned up
                    waitUntilNumberOfDelegateProxies(inst, 1, 1);
                    DelegateProxy newDelegateProxy3 = delegateProxyStore.getAllDelegateProxies().stream().findFirst().get();
                    Assertions.assertEquals(newDelegateProxy.get().getProxy().getId(), newDelegateProxy3.getProxy().getId());
                    Assertions.assertEquals("ac5969e6d722a0951143ba125846feeff4491b33", newDelegateProxy3.getProxySpecHash());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testCleanupPendingDelegateProxies(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (RedisServer redisServer = new RedisServer()) {
                try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-redis-3.yml", properties, true)) {
                    TestUtil.sleep(10_000);
                    waitUntilNumberOfDelegateProxies(inst, 1, 0, 1, 0);
                }
                // re-start instance with same config
                try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-redis-3.yml", properties, true)) {
                    inst.enableCleanup();
                    TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                    proxySharingScaler.disableCleanup();

                    // wait for scale-up to finish
                    // pending proxy should be marked as ToRemove
                    // wait for long time, leader election can take long
                    waitUntilNumberOfDelegateProxies(inst, 2, 1, 0, 1, 120_000);

                    // cleanup should remove old proxy
                    proxySharingScaler.enableCleanup();
                    waitUntilNumberOfDelegateProxies(inst, 1, 1, 0, 0);
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testSeatNotImmediatelyAvailable(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-4.yml", properties, true)) {
                inst.enableCleanup();
                // should be staring a new DelegateProxy
                waitUntilNumberOfDelegateProxies(inst, 1, 0, 1, 0);

                Instant start = Instant.now();
                String id = inst.client.startProxy("myApp");
                Instant stop = Instant.now();
                // start of app should have taken at least 50 seconds
                Assertions.assertTrue(Duration.between(start, stop).toSeconds() > 25);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testSeatNotImmediatelyAvailableAndAppStopped(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-4.yml", properties, true)) {
                inst.enableCleanup();
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                proxySharingScaler.disableCleanup();
                IDelegateProxyStore delegateProxyStore = proxySharingScaler.getDelegateProxyStore();

                // should be staring a new DelegateProxy
                waitUntilNumberOfDelegateProxies(inst, 1, 0, 1, 0);
                DelegateProxy delegateProxy = delegateProxyStore.getAllDelegateProxies().stream().findFirst().get();
                Assertions.assertEquals(DelegateProxyStatus.Pending, delegateProxy.getDelegateProxyStatus());

                // start app (without waiting for it to be ready)
                String id = inst.client.startProxy("myApp", null, false);
                TestUtil.sleep(5_000);

                // stop app
                inst.client.stopProxy(id);

                // DelegateProxy should not get claimed and become available
                waitUntilNumberOfDelegateProxies(inst, 2, 2, 0, 0);
                // proxy should not be claimed and still exists
                DelegateProxy delegateProxy2 = delegateProxyStore.getDelegateProxy(delegateProxy.getProxy().getId());
                Assertions.assertEquals(DelegateProxyStatus.Available, delegateProxy2.getDelegateProxyStatus());
                Assertions.assertEquals(0, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(2, proxySharingScaler.getNumUnclaimedSeats());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testSeatTimeout(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-5.yml", properties, true)) {
                inst.enableCleanup();
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                proxySharingScaler.disableCleanup();
                IDelegateProxyStore delegateProxyStore = proxySharingScaler.getDelegateProxyStore();

                // should be staring a new DelegateProxy
                waitUntilNumberOfDelegateProxies(inst, 1, 0, 1, 0);
                DelegateProxy delegateProxy = delegateProxyStore.getAllDelegateProxies().stream().findFirst().get();
                Assertions.assertEquals(DelegateProxyStatus.Pending, delegateProxy.getDelegateProxyStatus());

                Instant start = Instant.now();
                JsonObject error = inst.client.startProxyError("myApp", null);
                Instant stop = Instant.now();

                Assertions.assertEquals("Failed to start proxy", error.getString("data"));

                // start of app should have taken at less than 15 seconds
                Assertions.assertTrue(Duration.between(start, stop).toSeconds() < 15);

                // DelegateProxy should not get claimed and become available
                waitUntilNumberOfDelegateProxies(inst, 2, 2, 0, 0);
                // proxy should not be claimed and still exists
                DelegateProxy delegateProxy2 = delegateProxyStore.getDelegateProxy(delegateProxy.getProxy().getId());
                Assertions.assertEquals(DelegateProxyStatus.Available, delegateProxy2.getDelegateProxyStatus());
                Assertions.assertEquals(0, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(2, proxySharingScaler.getNumUnclaimedSeats());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testMultiSeat1(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-6.yml", properties, true)) {
                inst.enableCleanup();
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                // there should be one DelegateProxy and three seats
                waitUntilNumberOfDelegateProxies(inst, 1, 1);
                Assertions.assertEquals(0, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(3, proxySharingScaler.getNumUnclaimedSeats());

                String id1 = inst.client.startProxy("myApp");
                Proxy proxy1 = inst.proxyService.getProxy(id1);
                inst.client.testProxyReachable(proxy1.getTargetId());

                String id2 = inst.client.startProxy("myApp");
                Proxy proxy2 = inst.proxyService.getProxy(id2);
                inst.client.testProxyReachable(proxy2.getTargetId());

                // target ids should be equal
                Assertions.assertEquals(proxy1.getTargetId(), proxy2.getTargetId());

                // no scale-up should have happened
                waitUntilNumberOfDelegateProxies(inst, 1, 1);
                Assertions.assertEquals(2, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());

                String id3 = inst.client.startProxy("myApp");
                Proxy proxy3 = inst.proxyService.getProxy(id3);
                inst.client.testProxyReachable(proxy3.getTargetId());

                // target ids should be equal
                Assertions.assertEquals(proxy3.getTargetId(), proxy2.getTargetId());

                // scale-up should have happened
                waitUntilNumberOfDelegateProxies(inst, 2, 2);
                Assertions.assertEquals(3, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(3, proxySharingScaler.getNumUnclaimedSeats());

                // stop first app
                inst.client.stopProxy(id1);

                // fore reconcile
                proxySharingScaler.scheduleReconcile();

                // should have scaled-down
                waitUntilNumberOfDelegateProxies(inst, 1, 1);
                Assertions.assertEquals(2, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testMultiSeat2(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-7.yml", properties, true)) {
                inst.enableCleanup();
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                // there should be one DelegateProxy and two seats
                waitUntilNumberOfDelegateProxies(inst, 1, 1);
                Assertions.assertEquals(0, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(2, proxySharingScaler.getNumUnclaimedSeats());

                String id1 = inst.client.startProxy("myApp");
                Proxy proxy1 = inst.proxyService.getProxy(id1);
                inst.client.testProxyReachable(proxy1.getTargetId());

                String id2 = inst.client.startProxy("myApp");
                Proxy proxy2 = inst.proxyService.getProxy(id2);
                inst.client.testProxyReachable(proxy2.getTargetId());

                String id3 = inst.client.startProxy("myApp");
                Proxy proxy3 = inst.proxyService.getProxy(id3);
                inst.client.testProxyReachable(proxy3.getTargetId());

                // scale-up should have happened
                waitUntilNumberOfDelegateProxies(inst, 2, 2);
                Assertions.assertEquals(3, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());

                // stop first app, both delegateProxies will have one claimed and one unclaimed seat
                inst.client.stopProxy(id1);

                // fore reconcile
                proxySharingScaler.scheduleReconcile();

                // should not be trying to scale down, since the amount of unclaimed seats is less than the amount of containers per seat
                Assertions.assertEquals(ProxySharingScaler.ReconcileStatus.Stable, proxySharingScaler.getLastReconcileStatus());
                waitUntilNumberOfDelegateProxies(inst, 2, 2);
                Assertions.assertEquals(2, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(2, proxySharingScaler.getNumUnclaimedSeats());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testMultiSeat3(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization-7.yml", properties, true)) {
                inst.enableCleanup();
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
                // there should be one DelegateProxy and two seats
                waitUntilNumberOfDelegateProxies(inst, 1, 1);
                Assertions.assertEquals(0, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(2, proxySharingScaler.getNumUnclaimedSeats());

                // launch 5 apps
                // DelegateProxy 1: id1 & id2
                // DelegateProxy 2: id3 & id4
                // DelegateProxy 3: id5

                String id1 = inst.client.startProxy("myApp");
                Proxy proxy1 = inst.proxyService.getProxy(id1);
                inst.client.testProxyReachable(proxy1.getTargetId());

                String id2 = inst.client.startProxy("myApp");
                Proxy proxy2 = inst.proxyService.getProxy(id2);
                inst.client.testProxyReachable(proxy2.getTargetId());

                String id3 = inst.client.startProxy("myApp");
                Proxy proxy3 = inst.proxyService.getProxy(id3);
                inst.client.testProxyReachable(proxy3.getTargetId());

                String id4 = inst.client.startProxy("myApp");
                Proxy proxy4 = inst.proxyService.getProxy(id4);
                inst.client.testProxyReachable(proxy4.getTargetId());

                String id5 = inst.client.startProxy("myApp");
                Proxy proxy5 = inst.proxyService.getProxy(id5);
                inst.client.testProxyReachable(proxy5.getTargetId());

                // scale-up should have happened
                waitUntilNumberOfDelegateProxies(inst, 3, 3);
                Assertions.assertEquals(5, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());

                // stop apps
                inst.client.stopProxy(id1);
                inst.client.stopProxy(id3);

                // fore reconcile
                proxySharingScaler.scheduleReconcile();
                TestUtil.sleep(5_000);

                // every DelegateProxy has both a claimed and unclaimed seat now
                // the number of unclaimedSeats (3) is higher than the seats-per-container (2)
                // -> should try to scale down
                Assertions.assertEquals(ProxySharingScaler.ReconcileStatus.ScaleDown, proxySharingScaler.getLastReconcileStatus());
                // but not scaling down because every DelegateProxy has a claimed seat
                waitUntilNumberOfDelegateProxies(inst, 3, 3);
                Assertions.assertEquals(3, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(3, proxySharingScaler.getNumUnclaimedSeats());
            }
        }
    }

    private void waitUntilNoPendingSeats(ShinyProxyInstance inst) {
        TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
        boolean noPendingSeats = Retrying.retry((c, m) -> {
            return proxySharingScaler.getNumPendingSeats() == 0;
        }, 60_000, "assert no pending seats", 1, true);
        Assertions.assertTrue(noPendingSeats);
    }

    private void waitUntilUnNumberOfUnClaimedSeats(ShinyProxyInstance inst, int numSeats) {
        TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
        boolean noPendingSeats = Retrying.retry((c, m) -> {
            return proxySharingScaler.getNumUnclaimedSeats() == numSeats;
        }, 180_000, "assert number of unclaimed seats", 1, true);
        Assertions.assertTrue(noPendingSeats);
    }

    private void waitUntilNumberOfDelegateProxies(ShinyProxyInstance inst, int numDelegateProxies, int numAvailable) {
        waitUntilNumberOfDelegateProxies(inst, numDelegateProxies, numAvailable, 0, 0);
    }

    private void waitUntilNumberOfDelegateProxies(ShinyProxyInstance inst, int numDelegateProxies, int numAvailable, int numPending, int numRemove) {
        waitUntilNumberOfDelegateProxies(inst, numDelegateProxies, numAvailable, numPending, numRemove, 60_000);
    }

    private void waitUntilNumberOfDelegateProxies(ShinyProxyInstance inst, int numDelegateProxies, int numAvailable, int numPending, int numRemove, int delay) {
        TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_myApp", TestProxySharingScaler.class);
        IDelegateProxyStore delegateProxyStore = proxySharingScaler.getDelegateProxyStore();
        boolean noPendingSeats = Retrying.retry((c, m) -> {
            return delegateProxyStore.getAllDelegateProxies().size() == numDelegateProxies
                && delegateProxyStore.getAllDelegateProxies(DelegateProxyStatus.Available).count() == numAvailable
                && delegateProxyStore.getAllDelegateProxies(DelegateProxyStatus.Pending).count() == numPending
                && delegateProxyStore.getAllDelegateProxies(DelegateProxyStatus.ToRemove).count() == numRemove;
        }, 120_000, "assert number delegated proxies", 1, true);
        Assertions.assertTrue(noPendingSeats,
            String.format("Total: %s, Available: %s, Pending: %s, ToRemove: %s",
                delegateProxyStore.getAllDelegateProxies().size(),
                delegateProxyStore.getAllDelegateProxies(DelegateProxyStatus.Available).count(),
                delegateProxyStore.getAllDelegateProxies(DelegateProxyStatus.Pending).count(),
                delegateProxyStore.getAllDelegateProxies(DelegateProxyStatus.ToRemove).count()
            )
        );
    }

    private void waitUntilDelegateProxyIsToRemove(TestProxySharingScaler proxySharingScaler, String delegateProxyId) {
        boolean noPendingSeats = Retrying.retry((c, m) -> {
            return proxySharingScaler.getDelegateProxyStore().getDelegateProxy(delegateProxyId).getDelegateProxyStatus() == DelegateProxyStatus.ToRemove;
        }, 60_000, "assert number delegated proxies", 1, true);
        Assertions.assertTrue(noPendingSeats);
    }

}
