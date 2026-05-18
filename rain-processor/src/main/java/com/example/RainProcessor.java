package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Properties;

public class RainProcessor {

    public static void main(String[] args) {

        // Kafka Streams Configuration
        Properties props = new Properties();

        props.put(
                StreamsConfig.APPLICATION_ID_CONFIG,
                "rain-processor-app"
        );

        props.put(
                StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092"
        );

        props.put(
                StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                Serdes.String().getClass()
        );

        props.put(
                StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                Serdes.String().getClass()
        );

        // Create Stream Builder
        StreamsBuilder builder = new StreamsBuilder();

        // Read from weather-topic
        KStream<String, String> weatherStream =
                builder.stream("weather-topic");

        ObjectMapper mapper = new ObjectMapper();

        // Process stream
        KStream<String, String> rainAlerts = weatherStream.flatMapValues(value -> {

            try {

                JsonNode jsonNode = mapper.readTree(value);

                int humidity = jsonNode
                        .get("weather")
                        .get("humidity")
                        .asInt();

                long stationId = jsonNode
                        .get("station_id")
                        .asLong();

                System.out.println(
                        "Received message from station "
                                + stationId
                                + " with humidity = "
                                + humidity
                );

                // Rain condition
                if (humidity > 70) {

                    String alertJson =
                            "{"
                                    + "\"station_id\":" + stationId + ","
                                    + "\"alert\":\"RAINING\""
                                    + "}";

                    System.out.println(
                            "Rain detected at station "
                                    + stationId
                    );

                    return java.util.Collections.singletonList(alertJson);
                }

            } catch (Exception e) {

                System.out.println("Invalid message: " + value);

                e.printStackTrace();
            }

            return java.util.Collections.emptyList();
        });

        // Send alerts to rain-alerts topic
        rainAlerts.to("rain-alerts");

        // Start Kafka Streams
        KafkaStreams streams =
                new KafkaStreams(builder.build(), props);

        streams.start();

        System.out.println("Rain Processor Started...");

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(
                new Thread(streams::close)
        );
    }
}