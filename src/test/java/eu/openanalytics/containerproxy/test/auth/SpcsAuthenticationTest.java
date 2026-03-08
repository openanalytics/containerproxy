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
package eu.openanalytics.containerproxy.test.auth;

import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SpcsAuthenticationTest {

    private static final String SNOWFLAKE_SERVICE_NAME_ENV = "SNOWFLAKE_SERVICE_NAME";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(120))
            .requestTimeout(Duration.ofSeconds(120))
            .followRedirect(HttpClient.Redirect.NEVER)
            .build();

    private String originalServiceName;

    @BeforeEach
    public void setUp() {
        // Save original value and set SNOWFLAKE_SERVICE_NAME for SPCS authentication
        // Spring Environment can read from system properties or environment variables
        originalServiceName = System.getProperty(SNOWFLAKE_SERVICE_NAME_ENV);
        if (originalServiceName == null) {
            originalServiceName = System.getenv(SNOWFLAKE_SERVICE_NAME_ENV);
        }
        System.setProperty(SNOWFLAKE_SERVICE_NAME_ENV, "TEST_SERVICE");
    }

    @AfterEach
    public void tearDown() {
        // Restore original value
        if (originalServiceName != null) {
            System.setProperty(SNOWFLAKE_SERVICE_NAME_ENV, originalServiceName);
        } else {
            System.clearProperty(SNOWFLAKE_SERVICE_NAME_ENV);
        }
    }

    // curl -v http://localhost:7583/api/proxy -H "Sf-Context-Current-User: TEST_USER_123"
    // should return 200
    @Test
    public void authenticateUserWithHeader() throws Exception {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-spcs-auth.yml")) {
            String baseUrl = "http://localhost:7583";
            String testUsername = "TEST_USER_123";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/proxy"))
                    .header("Sf-Context-Current-User", testUsername)
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(200, response.statusCode());
            Assertions.assertFalse(response.previousResponse().isPresent());
        }
    }

    // curl -v http://localhost:7583/api/proxy -H "Sf-Context-Current-User: TEST_USER_123" -H "Sf-Context-Current-User-Token: TEST_TOKEN_123"
    // should return 200
    @Test
    public void authenticateUserWithHeaderAndToken() throws Exception {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-spcs-auth.yml")) {
            String baseUrl = "http://localhost:7583";
            String testUsername = "TEST_USER_456";
            String testToken = "TEST_TOKEN_123";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/proxy"))
                    .header("Sf-Context-Current-User", testUsername)
                    .header("Sf-Context-Current-User-Token", testToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(200, response.statusCode());
            Assertions.assertFalse(response.previousResponse().isPresent());
        }
    }

    // curl -v http://localhost:7583/api/proxy 
    // should return 401
    @Test
    public void authenticationFailsWithoutHeader() throws Exception {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-spcs-auth.yml")) {
            String baseUrl = "http://localhost:7583";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/proxy"))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(401, response.statusCode());
            String responseBody = response.body() != null ? response.body() : "";
            Assertions.assertTrue(responseBody.contains("SPCS authentication failed"));
        }
    }

    // curl -v http://localhost:7583/api/proxy   -H "Sf-Context-Current-User: "
    // should return 401
    @Test
    public void authenticationFailsWithEmptyHeader() throws Exception {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-spcs-auth.yml")) {
            String baseUrl = "http://localhost:7583";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/proxy"))
                    .header("Sf-Context-Current-User", "")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(401, response.statusCode());
            String responseBody = response.body() != null ? response.body() : "";
            Assertions.assertTrue(responseBody.contains("SPCS authentication failed"));
        }
    }
}

