package com.example;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class ElasticService {

    public static void sendToElastic(String json) {

        try {

            CloseableHttpClient client = HttpClients.createDefault();

            HttpPost request = new HttpPost(
                "https://localhost:9200/weather-data/_doc"
            );

            request.setHeader("Content-Type", "application/json");

            String auth = "elastic:f1zJSg_BvxcOdcoWxJKc";
            String encodedAuth = java.util.Base64.getEncoder()
                    .encodeToString(auth.getBytes());

            request.setHeader(
                "Authorization",
                "Basic " + encodedAuth
            );

            request.setEntity(new StringEntity(json));

            client.execute(request);

            client.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}