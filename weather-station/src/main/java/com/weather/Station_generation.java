package com.weather;

import java.util.Random;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;

public class Station_generation implements Runnable {

    static int sequence_number = 0;
    static Random rand = new Random();
    static ObjectMapper mapper = new ObjectMapper();
    
    private KafkaProducer<String, String> producer; 
    private long station_id;

    public Station_generation(long station_id, KafkaProducer<String, String> producer) {
        this.station_id = station_id;
        this.producer = producer;
    }

    public static int generate_humidity() {
        return rand.nextInt(100);
    }

    public static int generate_temperature() {
        return rand.nextInt(50);
    }

    public static int generate_wind_speed() {
        return rand.nextInt(150);
    }

    @Override
    public void run() {
        while (true) {
            try {
                int battery_level = rand.nextInt(100);
                String battery_status;

                if (battery_level < 30) {
                    battery_status = "Low";
                } else if (battery_level < 70) {
                    battery_status = "Medium";
                } else {
                    battery_status = "High";
                }

                int drop_msg = rand.nextInt(10);

                if (drop_msg == 0) {
                    System.out.println("Message dropped");
                    sequence_number++;
                    Thread.sleep(1000);
                    continue;
                }

                Weather weather1 = new Weather(
                        generate_humidity(),
                        generate_temperature(),
                        generate_wind_speed()
                );

                Weather_station station1 = new Weather_station(
                        station_id,
                        weather1,
                        sequence_number,
                        battery_status,
                        System.currentTimeMillis()
                );

                String jsonString = mapper.writeValueAsString(station1);
                
                ProducerRecord<String, String> record = new ProducerRecord<>("weather-topic", Long.toString(station1.station_id), jsonString);

                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        System.err.println("Failed to send message from station " + station_id + ": " + exception.getMessage());
                    } else {
                        System.out.println("Sent message from station " + station_id + " to partition " + metadata.partition() + " at offset " + metadata.offset());
                    }
                });

                System.out.println("Station " + station_id + " running on thread " + Thread.currentThread().getName());
                System.out.println("[Station " + station_id + "] " + jsonString);

                sequence_number++;
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}