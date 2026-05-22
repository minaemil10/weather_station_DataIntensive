package com.example.service;

import java.io.IOException;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;

import com.example.WeatherMessage;

public class ParquetWriterService {
    private int fileCounter = 0;

    private static final String SCHEMA_JSON =
        "{" +
        "  \"type\": \"record\"," +
        "  \"name\": \"Weather\"," +
        "  \"fields\": [" +
        "    {\"name\": \"station_id\",   \"type\": \"long\"}," +
        "    {\"name\": \"humidity\",     \"type\": \"int\"}," +
        "    {\"name\": \"temperature\",  \"type\": \"int\"}," +
        "    {\"name\": \"wind_speed\",   \"type\": \"int\"}," +
        "    {\"name\": \"battery_status\", \"type\": \"string\"}," +
        "    {\"name\": \"s_no\",         \"type\": \"long\"}," +
        "    {\"name\": \"status_timestamp\", \"type\": \"long\"}" +
        "  ]" +
        "}";

    public void writeParquetFile(List<WeatherMessage> records) throws IOException {
        Schema schema = new Schema.Parser().parse(SCHEMA_JSON);
    String outputDir = System.getenv("OUTPUT_DIR") != null ? System.getenv("OUTPUT_DIR") : "data";
    new java.io.File(outputDir).mkdirs();
   String fileName = outputDir + "/weather-data-" + System.currentTimeMillis() + ".parquet";
        ParquetWriter<GenericRecord> writer = AvroParquetWriter
                .<GenericRecord>builder(new Path(fileName))
                .withSchema(schema)
                .withConf(new Configuration())
                .build();
        for (WeatherMessage msg : records) {
            GenericRecord record = new GenericData.Record(schema);
            record.put("station_id",       msg.station_id);
            record.put("humidity",         msg.weather.humidity);
            record.put("temperature",      msg.weather.temperature);
            record.put("wind_speed",       msg.weather.wind_speed);
            record.put("battery_status",   msg.battery_status);
            record.put("s_no",             msg.s_no);
            record.put("status_timestamp", msg.status_timestamp);
            writer.write(record);
        }
        writer.close();
    }
}
