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
package eu.openanalytics.containerproxy.test.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.util.Retrying;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ShinyProxyClient {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final String baseUrl;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ShinyProxyClient(String usernameAndPassword, int port) {
        this.baseUrl = "http://localhost:" + port;
        client = new OkHttpClient.Builder()
            .addInterceptor(new BasicAuthInterceptor(usernameAndPassword, usernameAndPassword))
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();
    }

    public String startProxy(String specId) {
        return startProxy(specId, null);
    }

    public String startProxy(String specId, Map<String, String> parameters) {
        RequestBody body = RequestBody.create("", null);
        if (parameters != null) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                body = RequestBody.create(
                    objectMapper.writeValueAsString(new HashMap<String, Object>() {{
                        put("parameters", parameters);
                    }}), MediaType.get("application/json"));
            } catch (JsonProcessingException e) {
                throw new TestHelperException("JSON error", e);
            }
        }
        Request request = new Request.Builder()
            .post(body)
            .url(baseUrl + "/api/proxy/" + specId)
            .build();

        JsonObject response = call(request, 201);
        String id = response.getJsonObject("data").getString("id");
        ProxyStatus proxyStatus = waitForProxyStatus(id);
        if (proxyStatus != ProxyStatus.Up) {
            throw new TestHelperException(String.format("Proxy with id %s failed to reach Up status", id));
        }
        return id;
    }

    public JsonObject startProxyError(String specId, Map<String, String> parameters) {
        RequestBody body = RequestBody.create("", null);
        if (parameters != null) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                body = RequestBody.create(objectMapper.writeValueAsString(Map.of("parameters", parameters)), MediaType.get("application/json"));
            } catch (JsonProcessingException e) {
                throw new TestHelperException("JSON error", e);
            }
        }
        Request request = new Request.Builder()
            .post(body)
            .url(baseUrl + "/api/proxy/" + specId)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.body() == null) {
                return null;
            }
            JsonReader jsonReader = Json.createReader(response.body().byteStream());
            JsonObject object = jsonReader.readObject();
            jsonReader.close();
            return object;
        } catch (Throwable t) {
            throw new TestHelperException("Error during http request", t);
        }
    }

    public ProxyStatus waitForProxyStatus(String proxyId) {
        for (int i = 0; i < 3; i++) {
            Request request = new Request.Builder()
                .get()
                .url(baseUrl + "/api/proxy/" + proxyId + "/status?watch=true&timeout=60")
                .build();

            JsonObject response = call(request, 200);
            ProxyStatus proxyStatus = ProxyStatus.valueOf(response.getJsonObject("data").getString("status"));
            if (proxyStatus.equals(ProxyStatus.Up) || proxyStatus.equals(ProxyStatus.Stopped) || proxyStatus.equals(ProxyStatus.Paused)) {
                return proxyStatus;
            }
        }
        throw new TestHelperException(String.format("Proxy with id %s failed to reach status", proxyId));
    }

    public void stopProxy(String proxyId) {
        Request request = new Request.Builder()
            .put(RequestBody.create("{\"status\":\"Stopping\"}", JSON))
            .url(baseUrl + "/api/proxy/" + proxyId + "/status")
            .build();

        call(request, 200);
        ProxyStatus status = waitForProxyStatus(proxyId);
        if (status != ProxyStatus.Stopped) {
            throw new TestHelperException(String.format("Failed to stop proxy: %s, status: %s", proxyId, status));
        }
    }

    public HashSet<JsonObject> getProxies() {
        Request request = new Request.Builder()
            .get()
            .url(baseUrl + "/api/proxy")
            .build();

        JsonObject response = call(request, 200);
        HashSet<JsonObject> result = new HashSet<>();

        for (JsonObject proxy : response.getJsonArray("data").getValuesAs(JsonObject.class)) {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            proxy.forEach(builder::add);
            // remove startupTimestamp since it is different after app recovery
            builder.add("startupTimestamp", "null");
            result.add(builder.build());
        }

        return result;
    }

    public void testProxyReachable(String id) {
        boolean res = Retrying.retry((c, m) -> {
            try {
                Request request = new Request.Builder()
                    .get()
                    .url(baseUrl + "/api/route/" + id + "/")
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.code() == 200) {
                        return true;
                    }
                }
                return false;
            } catch (Throwable t) {
                throw new TestHelperException("Error during http request", t);
            }
        }, 60_000, "proxy is reachable", 1, true);
        if (!res) {
            throw new TestHelperException("Proxy not reachable");
        }
    }

    public boolean checkAlive() {
        // client without auth
        OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();

        Request request = new Request.Builder()
            .get()
            .url(baseUrl + "/logout-success")
            .build();

        try (Response response = client.newCall(request).execute()) {
            return response.code() == 200;
        } catch (Exception e) {
            return false;
        }

    }

    private JsonObject call(Request request, int expectedStatusCode) {
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == expectedStatusCode) {
                if (response.body() == null) {
                    throw new TestHelperException("Response null during http request");
                }
                JsonReader jsonReader = Json.createReader(response.body().byteStream());
                JsonObject object = jsonReader.readObject();
                jsonReader.close();
                return object;
            } else {
                if (response.body() != null) {
                    logger.info("Response body: " + response.body().string());
                }
                throw new TestHelperException(String.format("Unexpected status code %s (expected %s) during http request", response.code(), expectedStatusCode));
            }
        } catch (Throwable t) {
            throw new TestHelperException("Error during http request", t);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Call newCall(Request request) {
        return client.newCall(request);
    }
}
