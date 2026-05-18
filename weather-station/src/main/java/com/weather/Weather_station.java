package com.weather;

public class Weather_station {
    public long station_id;
    public Weather weather;
    public long s_no;
    public String battery_status;
    public long status_timestamp;

    // Constructor MUST be inside the class
    public Weather_station(long station_id, Weather weather, long s_no, String battery_status, long status_timestamp) {
        this.station_id = station_id;
        this.weather = weather;
        this.s_no = s_no;
        this.battery_status = battery_status;
        this.status_timestamp = status_timestamp;
    }
}