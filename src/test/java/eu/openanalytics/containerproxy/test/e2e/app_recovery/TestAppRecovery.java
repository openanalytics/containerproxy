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

import okhttp3.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class TestAppRecovery {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new BasicAuthInterceptor("demo", "demo"))
            .build();

    @Test
    public void simpleTest() throws IOException, InterruptedException {
        List<ShinyProxyInstance> instances = new ArrayList<>();
        try {
            // 1. create the instance
            ShinyProxyInstance instance1 = new ShinyProxyInstance("1", "application-app-recovery_docker.yml");
            instances.add(instance1);
            Assertions.assertTrue(instance1.start());

            // 2. create a proxy
            Assertions.assertTrue(startProxy("01_hello"));

            // 3. get defined proxies
            String originalBody = getProxies();
            Assertions.assertNotNull(originalBody);

            // 4. stop the instance
            instance1.stop();

            // 5. start the instance again
            ShinyProxyInstance instance2 = new ShinyProxyInstance("2", "application-app-recovery_docker.yml");
            instances.add(instance2);
            Assertions.assertTrue(instance2.start());

            // 6. get defined proxies
            String newBody = getProxies();
            Assertions.assertNotNull(newBody);

            // 7. assert that the responses are equal
            Assertions.assertEquals(originalBody, newBody);

            // 8. stop proxy
            JsonReader jsonReader = Json.createReader(new StringReader(newBody));
            JsonArray object = jsonReader.readArray();
            jsonReader.close();
            String id = object.get(0).asJsonObject().getString("id");
            Assertions.assertTrue(stopProxy(id));

            // 9. stop the instance
            instance2.stop();
        } finally {
            instances.forEach(ShinyProxyInstance::stop);
        }
    }

    private boolean startProxy(String specId) {
        Request request = new Request.Builder()
                .post(RequestBody.create(null, new byte[0]))
                .url("http://localhost:7583/api/proxy/" + specId)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.code() == 201;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean stopProxy(String proxyId) {
        Request request = new Request.Builder()
                .delete(RequestBody.create(null, new byte[0]))
                .url("http://localhost:7583/api/proxy/" + proxyId)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.code() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getProxies() {
        Request request = new Request.Builder()
                .get()
                .url("http://localhost:7583/api/proxy/")
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        } catch (Exception e) {
            return null;
        }
    }

}
