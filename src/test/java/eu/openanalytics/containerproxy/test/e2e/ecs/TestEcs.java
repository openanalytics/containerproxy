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
package eu.openanalytics.containerproxy.test.e2e.ecs;

import eu.openanalytics.containerproxy.test.helpers.ShinyProxyClient;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TestEcs {


    @AfterEach
    public void waitForCleanup() throws InterruptedException {
        Thread.sleep(20_000);
    }

    @Test
    public void ecs_test() {
            ShinyProxyClient shinyProxyClient = new ShinyProxyClient("demo", "demo");
            List<ShinyProxyInstance> instances = new ArrayList<>();
            try {
                // 1. create the instance
                System.out.println("Starting instance");
                ShinyProxyInstance instance1 = new ShinyProxyInstance("1", "application-app-recovery_ecs.yml");
                instances.add(instance1);
                Assertions.assertTrue(instance1.start());
                System.out.println("Instance started");

                // 2. create a proxy
                String id = shinyProxyClient.startProxy("01_hello");
                Assertions.assertNotNull(id);

                // 3. stop the instance
                instance1.stop();

                // 4. start the instance again
                ShinyProxyInstance instance2 = new ShinyProxyInstance("2", "application-app-recovery_ecs.yml");
                instances.add(instance2);
                Assertions.assertTrue(instance2.start());

                // 5. get defined proxies
                HashSet<JsonObject> newProxies = shinyProxyClient.getProxies();
                Assertions.assertNotNull(newProxies);

                // 6. stop the proxy
                Assertions.assertTrue(shinyProxyClient.stopProxy(id));

                // 7. stop the instance
                instance2.stop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                instances.forEach(ShinyProxyInstance::stop);
            }
    }
}
