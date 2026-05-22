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
    private static volatile boolean running = true;

    public static void main(String[] args) {
        try {
            System.out.println("--- Starting Weather Central Station ---");

            // Read configuration from environment variables
            String dataDir = System.getenv("DATA_DIR");
            if (dataDir == null || dataDir.isEmpty()) {
                dataDir = "/data/bitcask";
            }
            System.out.println("[Config] Data directory: " + dataDir);

            String kafkaBrokers = System.getenv("KAFKA_BROKERS");
            if (kafkaBrokers == null || kafkaBrokers.isEmpty()) {
                kafkaBrokers = "localhost:9092";
            }
            System.out.println("[Config] Kafka brokers: " + kafkaBrokers);

            int port = 8080;
            String portEnv = System.getenv("SERVER_PORT");
            if (portEnv != null && !portEnv.isEmpty()) {
                try {
                    port = Integer.parseInt(portEnv);
                } catch (NumberFormatException e) {
                    System.out.println("[Config] Invalid SERVER_PORT, using default 8080");
                }
            }
            System.out.println("[Config] Server port: " + port);

            // 1. Initialize the Storage Engine
            BitCaskEngine storageEngine = new BitCaskEngine(dataDir);
            System.out.println("[Storage] BitCask Engine ready.");

            // 2. Start the Compactor in a regular (non-daemon) thread
            Compactor compactor = new Compactor(storageEngine);
            Thread compactorThread = new Thread(compactor, "Compactor");
            compactorThread.start();
            System.out.println("[Cleaner] Background Compactor started.");

            // 3. Start the Kafka Consumer Service
            KafkaConsumerService kafkaService = new KafkaConsumerService(storageEngine, kafkaBrokers);
            Thread kafkaThread = new Thread(kafkaService, "KafkaConsumer");
            kafkaThread.start();
            System.out.println("[Kafka] Consumer started.");

            // 4. Setup the HTTP Server
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Endpoint for single station: /weather?stationId=X
            server.createContext("/weather", new WeatherHandler(storageEngine));
            
            // Endpoint for all stations: /weather/all
            server.createContext("/weather/all", new AllWeatherHandler(storageEngine));
            
            // Health check endpoint for Kubernetes
            server.createContext("/health", new HealthCheckHandler());
            
            server.setExecutor(null);
            server.start();

            System.out.println("[API] Server listening on http://0.0.0.0:" + port);
            System.out.println("--- Central Station is Online ---");

            // Graceful shutdown handling
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[Shutdown] Initiating graceful shutdown...");
                running = false;
                server.stop(5);
                kafkaThread.interrupt();
                compactorThread.interrupt();
                System.out.println("[Shutdown] Central Station stopped.");
            }));

        } catch (IOException e) {
            System.err.println("Critical Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Health check endpoint for Kubernetes liveness/readiness probes
     */
    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (running) {
                sendResponse(exchange, "{\"status\":\"healthy\"}", 200);
            } else {
                sendResponse(exchange, "{\"status\":\"shutting down\"}", 503);
            }
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
