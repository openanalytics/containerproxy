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
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.ContainerDefinition;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.LogConfiguration;
import software.amazon.awssdk.services.ecs.model.LogDriver;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.amazon.awssdk.services.sts.StsClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestIntegrationOnEcs {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static EcsClient ecsClient;
    private static String cluster;

    private EcsClient getEcsClient() {
        if (ecsClient == null) {
            Region region = Region.of(System.getenv("ITEST_ECS_REGION"));
            cluster = System.getenv("ITEST_ECS_NAME");

            ecsClient = EcsClient.builder()
                .region(region)
                .build();
        }
        return ecsClient;
    }


    @Test
    public void launchProxy() {
        Assumptions.assumeTrue(checkAwsCredentials(), "Skipping ECS tests");
        try (ContainerSetup containerSetup = new ContainerSetup("ecs")) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-ecs.yml", Map.of(), true)) {
                inst.enableCleanup();
                // launch a proxy on ECS
                String id = inst.client.startProxy("01_hello");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(id);

                Task task = getTask(proxy);

                // get tags
                Map<String, String> tags = getTags(task);
                Assertions.assertEquals("demo", tags.get("openanalytics.eu/sp-user-id"));
                Assertions.assertEquals("01_hello", tags.get("openanalytics.eu/sp-spec-id"));
                Assertions.assertEquals("true", tags.get("openanalytics.eu/sp-proxied-app"));
                Assertions.assertEquals(proxy.getId(), tags.get("openanalytics.eu/sp-proxy-id"));
                Assertions.assertEquals("myvalue", tags.get("valid-label"));
                Assertions.assertFalse(tags.containsKey("invalid-label"));
                Assertions.assertFalse(tags.containsKey("invalid-label2"));

                Assertions.assertEquals("1024", task.cpu());
                Assertions.assertEquals("2048", task.memory());
                Assertions.assertEquals(21, task.ephemeralStorage().sizeInGiB());
                Assertions.assertEquals(false, task.enableExecuteCommand());
                Assertions.assertEquals(LaunchType.FARGATE, task.launchType());

                TaskDefinition taskDefinition = getTaskDefinition(task);

                Assertions.assertNull(taskDefinition.deregisteredAt());
                Assertions.assertNull(taskDefinition.executionRoleArn());
                Assertions.assertNull(taskDefinition.taskRoleArn());
                Assertions.assertTrue(taskDefinition.volumes().isEmpty());
                Assertions.assertEquals(1, taskDefinition.revision());
                Assertions.assertEquals(1, taskDefinition.containerDefinitions().size());
                ContainerDefinition containerDefinition = taskDefinition.containerDefinitions().get(0);
                Assertions.assertEquals(List.of("R", "-e", "shinyproxy::run_01_hello()"), containerDefinition.command());
                Assertions.assertEquals(List.of(), containerDefinition.dnsServers());
                Map<String, String> dockerLabels = containerDefinition.dockerLabels();
                Assertions.assertTrue(dockerLabels.size() > 15);
                Assertions.assertEquals("demo", dockerLabels.get("openanalytics.eu/sp-user-id"));
                Assertions.assertEquals("01_hello", dockerLabels.get("openanalytics.eu/sp-spec-id"));

                Map<String, String> environment = containerDefinition.environment().stream().collect(Collectors.toMap(KeyValuePair::name, KeyValuePair::value));
                Assertions.assertEquals(3, environment.size());
                Assertions.assertEquals("demo", environment.get("SHINYPROXY_USERNAME"));
                Assertions.assertEquals("", environment.get("SHINYPROXY_USERGROUPS"));
                Assertions.assertEquals("/api/route/" + proxy.getId() + "/", environment.get("SHINYPROXY_PUBLIC_PATH"));
                Assertions.assertEquals("openanalytics/shinyproxy-integration-test-app", containerDefinition.image());
                Assertions.assertNull(containerDefinition.privileged()); // fargate does not support privileged
                Assertions.assertNull(containerDefinition.hostname());
                Assertions.assertNull(containerDefinition.logConfiguration());

                inst.client.stopProxy(id);

                taskDefinition = getTaskDefinition(task);
                Assertions.assertNotNull(taskDefinition.deregisteredAt());
            }
        }
    }

    @Test
    public void launchProxyWithRoles() {
        Assumptions.assumeTrue(checkAwsCredentials(), "Skipping ECS tests");
        try (ContainerSetup containerSetup = new ContainerSetup("ecs")) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-ecs.yml", Map.of("proxy.ecs.enable-cloudwatch", "true"), true)) {
                inst.enableCleanup();
                // launch a proxy on ECS
                String id = inst.client.startProxy("01_hello_roles");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(id);

                Task task = getTask(proxy);

                Assertions.assertEquals(50, task.ephemeralStorage().sizeInGiB());
                Assertions.assertEquals(true, task.enableExecuteCommand());

                TaskDefinition taskDefinition = getTaskDefinition(task);

                Assertions.assertEquals(System.getenv("ITEST_ECS_EXECUTION_ROLE"), taskDefinition.executionRoleArn());
                Assertions.assertEquals(System.getenv("ITEST_ECS_TASK_ROLE"), taskDefinition.taskRoleArn());

                ContainerDefinition containerDefinition = taskDefinition.containerDefinitions().get(0);
                LogConfiguration logConfiguration = containerDefinition.logConfiguration();
                Assertions.assertNotNull(logConfiguration);
                Assertions.assertEquals(LogDriver.AWSLOGS, logConfiguration.logDriver());
                Assertions.assertEquals("/ecs/sp-" + proxy.getId(), logConfiguration.options().get("awslogs-group"));
                Assertions.assertEquals(System.getenv("ITEST_ECS_REGION"), logConfiguration.options().get("awslogs-region"));
                Assertions.assertEquals("true", logConfiguration.options().get("awslogs-create-group"));
                Assertions.assertEquals("ecs", logConfiguration.options().get("awslogs-stream-prefix"));

                inst.client.stopProxy(id);

                taskDefinition = getTaskDefinition(task);
                Assertions.assertNotNull(taskDefinition.deregisteredAt());
            }
        }
    }

    @Test
    public void testInvalidConfig1() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-1.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of ECS backend: need at least one subnet in proxy.ecs.subnets", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig2() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-2.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of ECS backend: proxy.ecs.region not set", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig3() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-3.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of ECS backend: proxy.ecs.cluster not set to name of cluster", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig4() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-4.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of ECS backend: config has 'privileged: true' configured, this is not supported by ECS fargated", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig5() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-5.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of specs: spec with id '01_hello' has non 'memory-request' configured, this is required for running on ECS fargate", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig6() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-6.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of specs: spec with id '01_hello' has non 'cpu-request' configured, this is required for running on ECS fargate", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig7() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-7.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of specs: spec with id '01_hello' has 'cpu-limit' configured, this is not supported by ECS fargate", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig8() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-8.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of specs: spec with id '01_hello' has 'memory-limit' configured, this is not supported by ECS fargate", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig9() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-9.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of specs: spec with id '01_hello' has 'privileged: true' configured, this is not supported by ECS fargate", rootCause.getMessage());
    }

    @Test
    public void testInvalidConfig10() {
        TestHelperException ex = Assertions.assertThrows(TestHelperException.class, () -> new ShinyProxyInstance("application-test-ecs-invalid-10.yml"));

        Throwable rootCause = Throwables.getRootCause(ex);
        Assertions.assertInstanceOf(IllegalStateException.class, rootCause);
        Assertions.assertEquals("Error in configuration of specs: spec with id '01_hello' has 'dns' configured, this is not supported by ECS fargate", rootCause.getMessage());
    }

    private Task getTask(Proxy proxy) {
        String taskArn = proxy.getContainers().get(0).getRuntimeValue(BackendContainerNameKey.inst);
        List<Task> tasks = getEcsClient().describeTasks(builder -> builder.cluster(cluster).tasks(taskArn)).tasks();
        Assertions.assertEquals(1, tasks.size());
        return tasks.get(0);
    }

    private Map<String, String> getTags(Task task) {
        return getEcsClient().listTagsForResource(b -> b.resourceArn(task.taskArn()))
            .tags()
            .stream()
            .collect(Collectors.toMap(Tag::key, Tag::value));
    }

    private TaskDefinition getTaskDefinition(Task task) {
        return getEcsClient().describeTaskDefinition(builder -> builder.taskDefinition(task.taskDefinitionArn())).taskDefinition();
    }

    private boolean requireEnvVar(String name) {
        if (System.getenv(name) == null) {
            logger.info("Env var {} missing, skipping ECS Tests", name);
            return false;
        }
        return true;
    }

    private boolean checkAwsCredentials() {
        try (StsClient client = StsClient.create()) {
            client.getCallerIdentity();

            return requireEnvVar("ITEST_ECS_NAME")
                && requireEnvVar("ITEST_ECS_REGION")
                && requireEnvVar("ITEST_ECS_SECURITY_GROUPS")
                && requireEnvVar("ITEST_ECS_SUBNETS")
                && requireEnvVar("ITEST_ECS_TASK_ROLE")
                && requireEnvVar("ITEST_ECS_EXECUTION_ROLE");
        } catch (SdkClientException ex) {
            return false;
        }
    }

}
