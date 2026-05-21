package com.example;

import com.example.service.ParquetWriterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class Consumer {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "weather-dashboard-" + UUID.randomUUID());
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("weather-topic"));

        ObjectMapper objectMapper = new ObjectMapper();
        List<WeatherMessage> buffer = new ArrayList<>();
        ParquetWriterService parquetService = new ParquetWriterService();

        try {
            while (true) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(100);
                    for (ConsumerRecord<String, String> record : records) {
                        WeatherMessage weather = objectMapper.readValue(record.value(), WeatherMessage.class);
                        buffer.add(weather);
                        System.out.println("Buffered: " + buffer.size());
                    }
                    if (buffer.size() >= 10000) {
                        parquetService.writeParquetFile(buffer);
                        System.out.println("Parquet file written.");
                        buffer.clear();
                    }
                } catch (Exception e) {
                    System.err.println("Error processing record: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Consumer error: " + e.getMessage());
        } finally {
            consumer.close();
        }
    }
}
