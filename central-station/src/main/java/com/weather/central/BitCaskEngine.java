package com.weather.central;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BitCaskEngine {
    private static final String DEFAULT_DATA_DIR = "/data/bitcask";
    private final String dataDir;
    private RandomAccessFile activeLogWriter;
    private final ConcurrentHashMap<String, IndexEntry> keyDir = new ConcurrentHashMap<>();
    private File activeFile;
    private String fileName;
    private int maxFileSize = 1024*10;
    
    /**
     * Constructor that uses default data directory (/data/bitcask)
     */
    public BitCaskEngine() throws IOException {
        this(DEFAULT_DATA_DIR);
    }
    
    /**
     * Constructor that accepts custom data directory for flexibility
     */
    public BitCaskEngine(String customDataDir) throws IOException {
        this.dataDir = customDataDir;
        File dir = new File(dataDir + "/active");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 1. Scan hint files first (fast — no value data, just index info)
        File archiveDir = new File(dataDir + "/archive");
        if (archiveDir.exists()) {
            File[] hintFiles = archiveDir.listFiles((d, name) -> name.endsWith(".hint"));
            if (hintFiles != null) {
                for (File hf : hintFiles) {
                    scanHintFile(hf);
                }
            }
        }

        // 2. Then scan active files (only updates keyDir if key is absent or newer)
        File[] files = dir.listFiles((d, name) -> name.endsWith(".data"));
        if (files != null && files.length > 0) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                scanFile(f);
            }
            this.activeFile = files[files.length - 1];
            fileName = activeFile.getName();
        } else {
            fileName = "0001.data";
            this.activeFile = new File(dir, fileName);
        }
        this.activeLogWriter = new RandomAccessFile(activeFile, "rw");
        this.activeLogWriter.seek(this.activeLogWriter.length());
    }
    private void scanFile(File file) throws IOException {
        if (file.length() == 0) return;
        try (RandomAccessFile scanner = new RandomAccessFile(file, "r")) {
            long fileLength = scanner.length();
            while (scanner.getFilePointer() < fileLength) {
                long recordStartOffset = scanner.getFilePointer();

                long timestamp = scanner.readLong();
                int keySize = scanner.readInt();
                int valueSize = scanner.readInt();

                byte[] keyBytes = new byte[keySize];
                scanner.readFully(keyBytes);
                String stationId = new String(keyBytes);

                long valueOffset = recordStartOffset + 8 + 4 + 4 + keySize;

                // Only put if key is not already loaded from a hint file, or if this is newer
                IndexEntry existing = keyDir.get(stationId);
                if (existing == null || timestamp > existing.timestamp) {
                    keyDir.put(stationId, new IndexEntry(file.getName(), valueOffset, valueSize, timestamp));
                }
                scanner.skipBytes(valueSize);
            }
        }
    }

    private void scanHintFile(File hintFile) throws IOException {
        // Hint file format (written by Compactor): [keySize][valueSize][offset][timestamp][key]
        // Derive the matching .data filename from the hint filename
        String dataFileName = hintFile.getName().replace(".hint", ".data");

        try (DataInputStream dis = new DataInputStream(new FileInputStream(hintFile))) {
            while (dis.available() > 0) {
                int keySize = dis.readInt();
                int valueSize = dis.readInt();
                long valueOffset = dis.readLong();
                long timestamp = dis.readLong();

                byte[] keyBytes = new byte[keySize];
                dis.readFully(keyBytes);
                String key = new String(keyBytes);

                // Put directly — hint files are scanned first, no duplicates to worry about
                keyDir.put(key, new IndexEntry(dataFileName, valueOffset, valueSize, timestamp));
            }
        }
    }
public synchronized void put(String key, String value) {
    try {
        // Reopen writer if closed unexpectedly
        if (activeLogWriter.getFilePointer() < 0) {
            activeLogWriter = new RandomAccessFile(activeFile, "rw");
            activeLogWriter.seek(activeLogWriter.length());
        }
        byte[] keyBytes = key.getBytes();
        byte[] valueBytes = value.getBytes();
        long offset = activeLogWriter.getFilePointer();
        long timestamp = System.currentTimeMillis();
        int key_size = keyBytes.length;
        int value_size = valueBytes.length;

        activeLogWriter.writeLong(timestamp);
        activeLogWriter.writeInt(key_size);
        activeLogWriter.writeInt(value_size);
        activeLogWriter.write(keyBytes);
        activeLogWriter.write(valueBytes);
        long valueOffset = offset + 8 + 4 + 4 + key_size;
        IndexEntry entry = new IndexEntry(activeFile.getName(), valueOffset, value_size, timestamp);
        keyDir.put(key, entry);

        if (activeLogWriter.getFilePointer() >= maxFileSize) {
            rotate();
        }
    } catch (IOException e) {
        try {
            // Writer was closed — reopen and retry once
            activeLogWriter = new RandomAccessFile(activeFile, "rw");
            activeLogWriter.seek(activeLogWriter.length());
            put(key, value);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    public String get(String key) {
        IndexEntry entry = keyDir.get(key);
        if (entry == null) {
            return "Not Found";
        }

        String filePath;
    if (entry.fileName.startsWith("merge_")) {
        filePath = dataDir + "/archive/" + entry.fileName;
    } else {
        filePath = dataDir + "/active/" + entry.fileName;
    }

        try (RandomAccessFile reader = new RandomAccessFile(filePath, "r")) {
            reader.seek(entry.offset);
            byte[] value = new byte[entry.totalSize];
            reader.readFully(value);
            return new String(value);
        } catch (Exception e) {
            e.printStackTrace();
            return "Not Found";
        }
    }

    public Map<String, String> getAllLatest(){
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, IndexEntry> entry : keyDir.entrySet()) {
            map.put(entry.getKey(), get(entry.getKey()));
        }
        return map;
    }

    public void rotate() {
        try {
            activeLogWriter.close();
            int nextFileNumber = Integer.parseInt(fileName.substring(0, 4)) + 1;
            String nextFileName = String.format("%04d.data", nextFileNumber);
            activeFile = new File(dataDir + "/active", nextFileName);
            activeLogWriter = new RandomAccessFile(activeFile, "rw");
            activeLogWriter.seek(activeLogWriter.length());
            fileName = nextFileName;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, IndexEntry> getKeyDir() {
        return keyDir;
    }

    public void updatePointerFromCompactor(String key, IndexEntry newEntry, IndexEntry expectedOldEntry){
        keyDir.replace(key, expectedOldEntry, newEntry);
    }

    public void close(){
        try {
            activeLogWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}