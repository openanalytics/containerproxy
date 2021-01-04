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
package eu.openanalytics.containerproxy.test.helpers;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;

public class ShinyProxyInstance {

    private ProcessBuilder processBuilder;
    private Process process;
    private int port;

    public ShinyProxyInstance(String id, String configFileName, int port, String extraArgs) {
        this.port = port;
        processBuilder = new ProcessBuilder("java", "-jar",
                "target/containerproxy-0.9.0-SNAPSHOT-exec.jar",
                "--spring.config.location=src/test/resources/" + configFileName,
                "--server.port=" + port,
                extraArgs)
                .redirectOutput(new File(String.format("shinyproxy_recovery_%s_stdout.log", id)))
                .redirectError(new File(String.format("shinyproxy_recovery_%s_stderr.log", id)));
    }

    public ShinyProxyInstance(String id, String configFileName, String extraArgs) {
        this(id, configFileName, 7583, extraArgs);
    }

    public ShinyProxyInstance(String id, String configFileName, int port) {
        this(id, configFileName, port, "");
    }

    public ShinyProxyInstance(String id, String configFileName) {
        this(id, configFileName, 7583, "");
    }

    public boolean start() throws IOException, InterruptedException {
        process = processBuilder.start();
        Thread.sleep(20000);

        return checkAlive();
    }

    public boolean checkAlive() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .get()
                .url("http://localhost:7583/")
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
