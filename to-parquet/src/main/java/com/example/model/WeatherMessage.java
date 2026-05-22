package com.example.model;

public class WeatherMessage {

    public long station_id;
    public Weather weather;
    public int s_no;
    public String battery_status;
    public long status_timestamp;

    public static class Weather {
        public int humidity;
        public int temperature;
        public int wind_speed;
    }
}