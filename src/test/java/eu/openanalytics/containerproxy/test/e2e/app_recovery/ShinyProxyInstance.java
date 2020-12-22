package eu.openanalytics.containerproxy.test.e2e.app_recovery;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;

public class ShinyProxyInstance {

    private ProcessBuilder processBuilder;
    private Process process;
    private int port;

    public ShinyProxyInstance(String id, String configFileName, int port) {
        // TODO port
        this.port = port;
        processBuilder = new ProcessBuilder("java", "-jar",
                "target/containerproxy-0.9.0-SNAPSHOT-exec.jar",
                "--spring.config.location=src/test/resources/" + configFileName,
                "--server.port=" + port)
                .redirectOutput(new File(String.format("shinyproxy_recovery_%s_stdout.log", id)))
                .redirectError(new File(String.format("shinyproxy_recovery_%s_stderr.log", id)));
    }

    public ShinyProxyInstance(String id, String configFileName) {
        this(id, configFileName, 7583);
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
