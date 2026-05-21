package com.example;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;

// Crucial new imports for handling credentials safely
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class ElasticService {

    public static void sendToElastic(String json) {
        try {
            TrustStrategy acceptingTrustStrategy =
                    (X509Certificate[] chain, String authType) -> true;

            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();

            SSLConnectionSocketFactory sslsf =
                    new SSLConnectionSocketFactory(
                            sslContext,
                            NoopHostnameVerifier.INSTANCE
                    );

            HttpClientConnectionManager connectionManager = 
                    PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(sslsf)
                            .build();

            CloseableHttpClient client = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            HttpPost request =
                    new HttpPost("https://localhost:9200/weather-data/_doc");

            // --- ADD THIS AUTHENTICATION BLOCK HERE ---
            String auth = "elastic:f1zJSg_BvxcOdcoWxJKc";
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            request.setHeader("Authorization", "Basic " + encodedAuth);
            // ------------------------------------------

            request.setHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(json));

            client.execute(request, response -> {
                System.out.println("Inserted into Elasticsearch. Status: " + response.getCode());
                return null;
            });

            client.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}