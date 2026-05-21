package com.weather.central;

public class IndexEntry {
    public final String fileName;
    public final long offset;
    public final int totalSize;
    public final long timestamp;

    public IndexEntry(String fileName, long offset, int totalSize, long timestamp) {
        this.fileName = fileName;
        this.offset = offset;
        this.totalSize = totalSize;
        this.timestamp = timestamp;
    }
}
