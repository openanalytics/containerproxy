/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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
package eu.openanalytics.containerproxy.test.e2e.app_recovery;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestAppRecovery {

    @Test
    public void simpleTest() throws IOException, InterruptedException {
        ShinyProxyClient shinyProxyClient = new ShinyProxyClient("demo", "demo");
        List<ShinyProxyInstance> instances = new ArrayList<>();
        try {
            // 1. create the instance
            ShinyProxyInstance instance1 = new ShinyProxyInstance("1", "application-app-recovery_docker.yml");
            instances.add(instance1);
            Assertions.assertTrue(instance1.start());

            // 2. create a proxy
            String id = shinyProxyClient.startProxy("01_hello");
            Assertions.assertNotNull(id);

            // 3. get defined proxies
            String originalBody = shinyProxyClient.getProxies();
            Assertions.assertNotNull(originalBody);

            // 4. stop the instance
            instance1.stop();

            // 5. start the instance again
            ShinyProxyInstance instance2 = new ShinyProxyInstance("2", "application-app-recovery_docker.yml");
            instances.add(instance2);
            Assertions.assertTrue(instance2.start());

            // 6. get defined proxies
            String newBody = shinyProxyClient.getProxies();
            Assertions.assertNotNull(newBody);

            // 7. assert that the responses are equal
            Assertions.assertEquals(originalBody, newBody);

            // 8. stop the proxy
            Assertions.assertTrue(shinyProxyClient.stopProxy(id));

            // 9. stop the instance
            instance2.stop();
        } finally {
            instances.forEach(ShinyProxyInstance::stop);
        }
    }


}
