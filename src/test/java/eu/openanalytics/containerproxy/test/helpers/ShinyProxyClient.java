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
package eu.openanalytics.containerproxy.test.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import okhttp3.*;

import javax.json.*;
import java.time.Duration;
import java.util.HashSet;

public class ShinyProxyClient {

    private final OkHttpClient client;
    private final String baseUrl;

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private ObjectMapper objectMapper = new ObjectMapper();

    public ShinyProxyClient(String username, String password, int port) {
        this.baseUrl = "http://localhost:" + port;
        client = new OkHttpClient.Builder()
                .addInterceptor(new BasicAuthInterceptor(username, password))
                .callTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(120))
                .build();
        objectMapper.registerModule(new JSR353Module());
    }

    public ShinyProxyClient(String username, String password) {
        this(username, password, 7583);
    }

    public String startProxy(String specId) {
        Request request = new Request.Builder()
                .post(RequestBody.create(null, new byte[0]))
                .url(baseUrl + "/api/proxy/" + specId)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 201) {
                JsonReader jsonReader = Json.createReader(response.body().byteStream());
                JsonObject object = jsonReader.readObject();
                jsonReader.close();
                return object.getJsonObject("data").getString("id");
            } else {
                System.out.println("BODY: " + response.body().string());
                System.out.println("CODE: " + response.code());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean stopProxy(String proxyId) {
        Request request = new Request.Builder()
                .put(RequestBody.create("{\"desiredState\":\"Stopping\"}", JSON))
                .url(baseUrl + "/api/" + proxyId + "/status")
                .build();

        try (Response response = client.newCall(request).execute()) {
            Thread.sleep(2_000);
            return response.code() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public HashSet<JsonObject> getProxies() {
        Request request = new Request.Builder()
                .get()
                .url(baseUrl + "/api/proxy/")
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonObject resp = objectMapper.readValue(response.body().byteStream(), JsonObject.class);

            HashSet<JsonObject> result = new HashSet<>();

            for (JsonObject proxy : resp.getJsonArray("data").getValuesAs(JsonObject.class)) {
                JsonObjectBuilder builder = Json.createObjectBuilder();
                proxy.forEach(builder::add);
                // remove startupTimestamp since it is different after app recovery
                builder.add("startupTimestamp", "null");
                result.add(builder.build());
            }

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean getProxyRequest(String id) {
        Request request = new Request.Builder()
                .get()
                .url(baseUrl + "/api/route/" + id + "/")
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.code() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
