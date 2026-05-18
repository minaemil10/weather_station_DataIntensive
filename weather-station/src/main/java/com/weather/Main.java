package com.weather;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {

        // 1. Set up Kafka Producer Properties
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        props.setProperty(ProducerConfig.RETRIES_CONFIG, "3");
        props.setProperty("linger.ms", "5");

        // 2. Create the Producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        // 3. Start 10 Weather Station Threads
        for (int i = 1; i <= 10; i++) {
            Station_generation station = new Station_generation(i, producer);
            Thread thread = new Thread(station);
            thread.start();
        }
        
        System.out.println("Started 10 weather station threads...");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down: flushing and closing Kafka producer...");
            try {
                producer.flush();
                producer.close();
            } catch (Exception e) {
                System.err.println("Error while closing producer: " + e.getMessage());
            }
        }));
    }
}