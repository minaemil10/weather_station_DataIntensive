package com.weather;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

public class Main {

    public static void main(String[] args) {

        String podName = System.getenv("HOSTNAME");

        int stationId = 0;

        try {
            if (podName != null) {
                stationId = Math.abs(podName.hashCode());
            }
        } catch (Exception e) {
            System.out.println("Could not generate station ID, defaulting to 0");
        }

 String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");

System.out.println("RAW ENV KAFKA_BOOTSTRAP_SERVERS = " + bootstrapServers);

// FORCE VALID VALUE ALWAYS
if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
    bootstrapServers = "kafka:9092";
}

System.out.println("FINAL bootstrap servers = " + bootstrapServers);

        System.out.println("Using Kafka bootstrap servers: " + bootstrapServers);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> producer = null;

        try {
            // Give K8s network DNS 3 seconds to settle before connecting
            Thread.sleep(3000);
            
            // Try to initialize the producer
            producer = new KafkaProducer<>(props);
            System.out.println("Kafka Producer connected successfully!");

        } catch (Exception e) {
            // This catches BOTH the InterruptedException from Thread.sleep 
            // AND any Kafka connection errors!
            System.err.println("CRITICAL ERROR: Failed to start Kafka Producer!");
            e.printStackTrace();
            
            // Force the app to exit with an error code so Kubernetes knows to restart it
            System.exit(1); 
        }

        // Only run the station if the producer successfully connected
        if (producer != null) {
            Station_generation station = new Station_generation(stationId, producer);
            station.run();
        }
    }
}