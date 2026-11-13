/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2026 Open Analytics
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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.AuthBackendKey;
import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mandas.docker.client.DefaultDockerClient;
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NoAuthenticationTest {

    @Test
    public void authenticateUser() throws Exception {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-no-auth.yml")) {
            String baseUrl = "http://localhost:7583";
            OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(120))
                .followRedirects(false)
                .build();

            Request request = new Request.Builder()
                .url(baseUrl + "/api/proxyspec")
                .build();

            try (Response response = client.newCall(request).execute()) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertFalse(response.isRedirect());
            }
        }
    }

    @Test
    public void authBackendIsExposedToApp() throws Exception {
        try (ContainerSetup containerSetup = new ContainerSetup("docker")) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-no-auth.yml");
                 DefaultDockerClient dockerClient = new JerseyDockerClientBuilder().fromEnv().build()) {
                String userId = UUID.randomUUID().toString();
                Authentication auth = new AnonymousAuthenticationToken("key", userId, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
                String proxyId = UUID.randomUUID().toString();
                inst.proxyService.startProxy(auth, inst.specProvider.getSpec("01_hello"), null, proxyId, Map.of()).run();
                Proxy proxy = inst.proxyService.getProxy(proxyId);

                Assertions.assertEquals("none", proxy.getRuntimeValue(AuthBackendKey.inst));
                List<String> env = dockerClient.inspectContainer(proxy.getContainers().getFirst().getId()).config().env();
                Assertions.assertTrue(env.contains("SHINYPROXY_AUTH_BACKEND=none"));

                inst.proxyService.stopProxy(null, proxy, true).run();
            }
        }
    }

}
