package com.weather.central;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CentralStationApp {

    public static void main(String[] args) {
        try {
            System.out.println("--- Starting Weather Central Station ---");

            // 1. Initialize the Storage Engine
            BitCaskEngine storageEngine = new BitCaskEngine();
            System.out.println("[Storage] BitCask Engine ready.");

            // 2. Start the Compactor in a background thread
            Compactor compactor = new Compactor(storageEngine);
            Thread compactorThread = new Thread(compactor);
            compactorThread.setDaemon(true);
            compactorThread.start();
            System.out.println("[Cleaner] Background Compactor started.");

            // 3. Start the Kafka Consumer Service
            KafkaConsumerService kafkaService = new KafkaConsumerService(storageEngine);
            Thread kafkaThread = new Thread(kafkaService);
            kafkaThread.setDaemon(true);
            kafkaThread.start();
            System.out.println("[Kafka] Consumer started.");

            // 4. Setup the HTTP Server
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            
            // Endpoint for single station: /weather?stationId=X
            server.createContext("/weather", new WeatherHandler(storageEngine));
            
            // Endpoint for all stations: /weather/all
            server.createContext("/weather/all", new AllWeatherHandler(storageEngine));
            
            server.setExecutor(null);
            server.start();

            System.out.println("[API] Server listening on http://localhost:8080");
            System.out.println("--- Central Station is Online ---");

        } catch (IOException e) {
            System.err.println("Critical Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles GET /weather?stationId=X
     */
    static class WeatherHandler implements HttpHandler {
        private final BitCaskEngine engine;

        public WeatherHandler(BitCaskEngine engine) {
            this.engine = engine;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, "Method Not Allowed", 405);
                return;
            }

            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String stationId = params.get("stationId");

            if (stationId == null || stationId.isEmpty()) {
                sendResponse(exchange, "Missing stationId parameter", 400);
                return;
            }

            String result = engine.get(stationId);
            
            if ("Not Found".equals(result)) {
                sendResponse(exchange, "Station Not Found", 404);
            } else {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, result, 200);
            }
        }
    }

    /**
     * Handles GET /weather/all
     */
    static class AllWeatherHandler implements HttpHandler {
        private final BitCaskEngine engine;

        public AllWeatherHandler(BitCaskEngine engine) {
            this.engine = engine;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, "Method Not Allowed", 405);
                return;
            }

            Map<String, String> allData = engine.getAllLatest();

            // Return as a JSON object: {"station_1": "{...}", "station_2": "{...}"}
            String jsonResponse = "{" + 
                allData.entrySet().stream()
                    .map(e -> "\"" + e.getKey() + "\": " + e.getValue())
                    .collect(Collectors.joining(",")) 
                + "}";

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            sendResponse(exchange, jsonResponse, 200);
        }
    }

    // Helper methods for handlers
    private static void sendResponse(HttpExchange exchange, String response, int code) throws IOException {
        byte[] bytes = response.getBytes();
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
