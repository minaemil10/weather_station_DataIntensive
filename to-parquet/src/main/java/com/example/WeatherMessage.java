package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherMessage {
    public long station_id;
    public WeatherData weather;
    public long s_no;
    public String battery_status;
    public long status_timestamp;

    public static class WeatherData {
        public int humidity;
        public int temperature;
        public int wind_speed;
    }
}
