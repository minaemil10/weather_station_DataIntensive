package com.example;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.example.service.ParquetWriterService;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Consumer {

    public static void main(String[] args) {

        Properties props = new Properties();

        String bootstrap = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrap == null || bootstrap.isBlank()) {
            bootstrap = "kafka:9092";
        }

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "weather-parquet-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("weather-topic"));

        ObjectMapper mapper = new ObjectMapper();
        List<WeatherMessage> buffer = new ArrayList<>();
        ParquetWriterService parquet = new ParquetWriterService();

        System.out.println("Parquet consumer started...");

        while (true) {

            ConsumerRecords<String, String> records =
                    consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    WeatherMessage msg =
                            mapper.readValue(record.value(), WeatherMessage.class);

                    buffer.add(msg);

                } catch (Exception e) {
                    System.out.println("Bad record: " + record.value());
                }
            }

            if (buffer.size() >= 1000) {
                try {
                    parquet.writeParquetFile(buffer);
                    System.out.println("Wrote parquet batch: " + buffer.size());
                    buffer.clear();
                } catch (Exception e) {
                    System.err.println("Parquet write failed: " + e.getMessage());
                }
            }
        }
    }
}