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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class ShinyProxyInstance {

    private ProcessBuilder processBuilder;
    private Process process;
    private int port;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ShinyProxyInstance(String configFileName, int port, String extraArgs) {
        this.port = port;

        int mgmtPort = port % 1000 + 9000;

        String uuid = java.util.UUID.randomUUID().toString();

        logger.info("Starting ShinyProxy server, with output in {}", uuid);

        processBuilder = new ProcessBuilder("java", "-jar",
                "target/containerproxy-app-recovery.jar",
                "--spring.config.location=src/test/resources/" + configFileName,
                "--server.port=" + port,
                "--management.server.port=" + mgmtPort,
                extraArgs)
                .redirectOutput(new File(String.format("shinyproxy_recovery_%s_stdout.log", uuid)))
                .redirectError(new File(String.format("shinyproxy_recovery_%s_stderr.log", uuid)));
    }

    public ShinyProxyInstance(String configFileName, String extraArgs) {
        this(configFileName, 7583, extraArgs);
    }

    public ShinyProxyInstance(String configFileName) {
        this(configFileName, 7583, "");
    }

    public boolean start() throws IOException, InterruptedException {
        process = processBuilder.start();

        for (int i = 0; i < 20; i++) {
            Thread.sleep(2_000);
            if (checkAlive()) {
                return true;
            }
        }

        return false;
    }

    public boolean checkAlive() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .get()
                .url("http://localhost:" + this.port)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.code() == 200;
        } catch (Exception e) {
            return false;
        }

    }

    public void stop() {
        process.destroy();
    }

}
