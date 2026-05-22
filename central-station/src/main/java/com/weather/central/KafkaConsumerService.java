package com.weather.central;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class KafkaConsumerService implements Runnable {
    private final BitCaskEngine engine;
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TOPIC = "weather-topic";
    private volatile boolean running = true;

    public KafkaConsumerService(BitCaskEngine engine) {
        this(engine, "localhost:9092");
    }

    public KafkaConsumerService(BitCaskEngine engine, String kafkaBrokers) {
        this.engine = engine;
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "central-station-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(TOPIC));
    }

    @Override
    public void run() {
        System.out.println("[Kafka] Listening to " + TOPIC + "...");
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                // Poll for new records every 100ms
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        String jsonValue = record.value();
                        
                        // Parse JSON to get station_id
                        JsonNode root = objectMapper.readTree(jsonValue);
                        String stationId = root.has("station_id") 
                                           ? root.get("station_id").asText() 
                                           : record.key(); // Fallback to record key

                        if (stationId != null) {
                            // Save to our storage engine!
                            engine.put(stationId, jsonValue);
                        }
                    } catch (Exception e) {
                        System.err.println("[Kafka] Error parsing record: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (!(e instanceof org.apache.kafka.common.errors.WakeupException)) {
                System.err.println("[Kafka] Consumer error: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            consumer.close();
            System.out.println("[Kafka] Consumer closed.");
        }
    }

    public void shutdown() {
        running = false;
        consumer.wakeup();
    }
}
