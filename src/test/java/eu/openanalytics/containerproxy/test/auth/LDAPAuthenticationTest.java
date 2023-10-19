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
package eu.openanalytics.containerproxy.test.auth;

import eu.openanalytics.containerproxy.test.helpers.BasicAuthInterceptor;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class LDAPAuthenticationTest {

	@Test
	public void authenticateUser() throws Exception {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-ldap-auth.yml")) {
            String username = "tesla";
            String password = "password";

            String baseUrl = "http://localhost:7583";
            OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new BasicAuthInterceptor(username, password))
                .callTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(120))
                .followRedirects(false)
                .build();


            Request request = new Request.Builder()
                .url(baseUrl + "/api/proxy")
                .build();

            try (Response response = client.newCall(request).execute()) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertFalse(response.isRedirect());
            }
        }
	}
}
