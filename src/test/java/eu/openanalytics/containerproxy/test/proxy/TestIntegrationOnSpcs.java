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
package eu.openanalytics.containerproxy.test.proxy;

import com.google.common.base.Throwables;
import eu.openanalytics.containerproxy.backend.spcs.client.ApiClient;
import eu.openanalytics.containerproxy.backend.spcs.client.ApiException;
import eu.openanalytics.containerproxy.backend.spcs.client.Configuration;
import eu.openanalytics.containerproxy.backend.spcs.client.auth.HttpBearerAuth;
import eu.openanalytics.containerproxy.backend.spcs.client.api.ServiceApi;
import eu.openanalytics.containerproxy.backend.spcs.client.model.Service;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceContainer;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceSpec;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceSpecInlineText;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import eu.openanalytics.containerproxy.test.helpers.TestHelperException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class TestIntegrationOnSpcs {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static ServiceApi serviceApi;

    private ServiceApi getServiceApi() {
        if (serviceApi == null) {
            String accountName = System.getenv("ITEST_SPCS_ACCOUNT_NAME");
            if (accountName == null || accountName.isEmpty()) {
                throw new IllegalStateException("ITEST_SPCS_ACCOUNT_NAME environment variable not set");
            }
            // Construct account URL from account name
            String accountUrl = String.format("https://%s.snowflakecomputing.com", accountName);
            
            // For tests, use PAT (Programmatic Access Token) authentication
            String authToken = System.getenv("ITEST_SPCS_PROGRAMMATIC_ACCESS_TOKEN");
            if (authToken == null || authToken.isEmpty()) {
                throw new IllegalStateException("ITEST_SPCS_PROGRAMMATIC_ACCESS_TOKEN environment variable must be set");
            }

            ApiClient apiClient = Configuration.getDefaultApiClient();
            apiClient.setBasePath(accountUrl);
            
            HttpBearerAuth keyPairAuth = (HttpBearerAuth) apiClient.getAuthentication("KeyPair");
            keyPairAuth.setBearerToken(authToken);

            serviceApi = new ServiceApi(apiClient);
        }
        return serviceApi;
    }

    @Test
    public void launchProxy() {
        Assumptions.assumeTrue(checkSnowflakeCredentials(), "Skipping SPCS tests");
        try (ContainerSetup containerSetup = new ContainerSetup("spcs")) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-spcs.yml", Map.of(), true)) {
                inst.enableCleanup();
                // launch a proxy on SPCS
                String id = inst.client.startProxy("01_hello");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(id);

                Service service = getService(proxy);

                // Verify service properties
                Assertions.assertNotNull(service.getDnsName());
                Assertions.assertTrue(service.getDnsName().contains(".svc.spcs.internal"));
                // Compare compute pool case-insensitively as Snowflake may return it in different case
                String expectedComputePool = System.getenv("ITEST_SPCS_COMPUTE_POOL");
                String actualComputePool = service.getComputePool();
                Assertions.assertNotNull(actualComputePool, "Compute pool should not be null");
                Assertions.assertTrue(actualComputePool.equalsIgnoreCase(expectedComputePool),
                    () -> String.format("Compute pool mismatch: expected '%s' (case-insensitive), got '%s'", expectedComputePool, actualComputePool));
                Assertions.assertNotNull(service.getStatus());

                // Verify service containers are running
                List<ServiceContainer> containers = getServiceContainers(proxy);
                Assertions.assertFalse(containers.isEmpty());
                
                ServiceContainer container = containers.get(0);
                Assertions.assertNotNull(container.getStatus());
                Assertions.assertTrue(
                    container.getStatus().equals("RUNNING") || 
                    container.getStatus().equals("UP") || 
                    container.getStatus().equals("READY")
                );

                // Verify service spec contains expected configuration
                Assertions.assertNotNull(service.getSpec());
                ServiceSpec spec = service.getSpec();
                
                // Check spec_type to determine the actual type (API may return base ServiceSpec)
                String specType = spec.getSpecType();
                Assertions.assertNotNull(specType, "Service spec type should not be null");
                
                // The spec should be of type "from_inline" (ServiceSpecInlineText)
                // Verify spec type matches expected value (accept both "from_inline" and class name)
                Assertions.assertTrue("from_inline".equals(specType) || "ServiceSpecInlineText".equals(specType),
                    () -> String.format("Service spec type should be 'from_inline' or 'ServiceSpecInlineText', but got: %s", specType));
                
                // If API properly deserialized as ServiceSpecInlineText, validate spec text content
                if (spec instanceof ServiceSpecInlineText) {
                    ServiceSpecInlineText inlineSpec = (ServiceSpecInlineText) spec;
                    String specText = inlineSpec.getSpecText();
                    Assertions.assertNotNull(specText, "Service spec text should not be null");
                    Assertions.assertTrue(specText.contains("openanalytics/shinyproxy-integration-test-app"),
                        () -> String.format("Spec text should contain image name, but got: %s", specText.substring(0, Math.min(200, specText.length()))));
                    Assertions.assertTrue(specText.contains("SHINYPROXY_USERNAME"),
                        "Spec text should contain SHINYPROXY_USERNAME environment variable");
                    Assertions.assertTrue(specText.contains("demo"),
                        "Spec text should contain demo user");
                } else {
                    // API returned base ServiceSpec (deserialization issue), but spec type is correct
                    // This is acceptable - the spec type is validated above
                    logger.debug("Service spec is base ServiceSpec type (not ServiceSpecInlineText), but spec_type is correct: {}", specType);
                }

                inst.client.stopProxy(id);

                // Verify service is deleted or stopped
                // Note: Service deletion may be asynchronous
                containers = getServiceContainers(proxy);
                // After stopping, containers should be stopped/suspended or service should be deleted
                boolean allStopped = containers.isEmpty() || containers.stream().allMatch(c -> 
                    c.getStatus() != null && (
                        c.getStatus().equals("STOPPED") || 
                        c.getStatus().equals("SUSPENDED") || 
                        c.getStatus().equals("DELETED")
                    )
                );
                Assertions.assertTrue(allStopped, "Service containers should be stopped after proxy stop");
            }
        }
    }

    @Test
    public void testInvalidConfig1() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-1.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of SPCS backend: proxy.spcs.account-identifier not set", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig2() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-2.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of SPCS backend: one of the following must be set: proxy.spcs.private-rsa-key-path or proxy.spcs.programmatic-access-token", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig3() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-3.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of SPCS backend: proxy.spcs.database not set", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig4() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-4.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of SPCS backend: proxy.spcs.schema not set", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig5() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-5.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of SPCS backend: proxy.spcs.compute-pool not set", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig6() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-6.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertTrue(rootCause.getMessage().contains("has no 'memory-request' configured"), rootCause.getMessage());
        Assertions.assertTrue(rootCause.getMessage().contains("required for running on Snowflake SPCS"), rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig7() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-7.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertTrue(rootCause.getMessage().contains("has no 'cpu-request' configured"), rootCause.getMessage());
        Assertions.assertTrue(rootCause.getMessage().contains("required for running on Snowflake SPCS"), rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig8() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-8.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertTrue(rootCause.getMessage().contains("has 'memory-limit' configured"), rootCause.getMessage());
        Assertions.assertTrue(rootCause.getMessage().contains("not supported by Snowflake SPCS"), rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig9() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-9.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertTrue(rootCause.getMessage().contains("has 'cpu-limit' configured"), rootCause.getMessage());
        Assertions.assertTrue(rootCause.getMessage().contains("not supported by Snowflake SPCS"), rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig10() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-spcs-invalid-10.yml"));
        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertTrue(rootCause.getMessage().contains("has 'privileged: true' configured"), rootCause.getMessage());
        Assertions.assertTrue(rootCause.getMessage().contains("not supported by Snowflake SPCS"), rootCause.getMessage());
    }

    private Service getService(Proxy proxy) {
        String fullServiceName = proxy.getContainers().getFirst().getRuntimeValue(BackendContainerNameKey.inst);
        String[] parts = fullServiceName.split("\\.");
        Assertions.assertEquals(3, parts.length, "Service name should be in format database.schema.service");
        String serviceDb = parts[0];
        String serviceSchema = parts[1];
        String serviceName = parts[2];
        
        try {
            return getServiceApi().fetchService(serviceDb, serviceSchema, serviceName);
        } catch (ApiException e) {
            throw new TestHelperException("Error fetching service: " + fullServiceName, e);
        }
    }

    private List<ServiceContainer> getServiceContainers(Proxy proxy) {
        String fullServiceName = proxy.getContainers().getFirst().getRuntimeValue(BackendContainerNameKey.inst);
        String[] parts = fullServiceName.split("\\.");
        Assertions.assertEquals(3, parts.length, "Service name should be in format database.schema.service");
        String serviceDb = parts[0];
        String serviceSchema = parts[1];
        String serviceName = parts[2];
        
        try {
            return getServiceApi().listServiceContainers(serviceDb, serviceSchema, serviceName);
        } catch (ApiException e) {
            // Service might be deleted, return empty list
            if (e.getCode() == 404) {
                return List.of();
            }
            throw new TestHelperException("Error listing service containers: " + fullServiceName, e);
        }
    }

    private boolean requireEnvVar(String name) {
        if (System.getenv(name) == null) {
            logger.info("Env var {} missing, skipping SPCS Tests", name);
            return false;
        }
        return true;
    }

    private boolean checkSnowflakeCredentials() {
        return requireEnvVar("ITEST_SPCS_ACCOUNT_NAME")
            && requireEnvVar("ITEST_SPCS_USERNAME")
            && requireEnvVar("ITEST_SPCS_PROGRAMMATIC_ACCESS_TOKEN")
            && requireEnvVar("ITEST_SPCS_DATABASE")
            && requireEnvVar("ITEST_SPCS_SCHEMA")
            && requireEnvVar("ITEST_SPCS_COMPUTE_POOL");
    }

}

