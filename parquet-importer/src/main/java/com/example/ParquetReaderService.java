package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;

public class ParquetReaderService {

    public static void main(String[] args) {

        String filePath = "data/weather-data.parquet";

        try {
            Path path = new Path(filePath);

            ParquetReader<GenericRecord> reader =
                    AvroParquetReader.<GenericRecord>builder(path).build();

            GenericRecord record;

            while ((record = reader.read()) != null) {
                String json = record.toString();
                ElasticService.sendToElastic(json);
            }

            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}