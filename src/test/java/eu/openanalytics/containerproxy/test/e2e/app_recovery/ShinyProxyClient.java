package eu.openanalytics.containerproxy.test.e2e.app_recovery;

import okhttp3.*;

import javax.json.*;
import java.util.HashSet;

public class ShinyProxyClient {

    private final OkHttpClient client;

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public ShinyProxyClient(String username, String password) {
        client = new OkHttpClient.Builder()
                .addInterceptor(new BasicAuthInterceptor(username, password))
                .build();
    }

    public String startProxy(String specId) {
        Request request = new Request.Builder()
                .post(RequestBody.create(null, new byte[0]))
                .url("http://localhost:7583/api/proxy/" + specId)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 201) {
                JsonReader jsonReader = Json.createReader(response.body().byteStream());
                JsonObject object = jsonReader.readObject();
                jsonReader.close();
                return object.getString("id");
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public boolean stopProxy(String proxyId) {
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

    public HashSet<JsonObject> getProxies() {
        Request request = new Request.Builder()
                .get()
                .url("http://localhost:7583/api/proxy/")
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonReader jsonReader = Json.createReader(response.body().byteStream());
            JsonArray array = jsonReader.readArray();
            jsonReader.close();

            HashSet<JsonObject> result = new HashSet();
            array.forEach(v -> result.add(v.asJsonObject()));

            return result;
        } catch (Exception e) {
            return null;
        }
    }


}
