package com.weather.central;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Compactor implements Runnable {
    private final BitCaskEngine engine;
    private static final String ACTIVE_DIR = "central-station/data/active/";
    private static final String ARCHIVE_DIR = "central-station/data/archive/";

    public Compactor(BitCaskEngine engine) {
        this.engine = engine;
        File arch = new File(ARCHIVE_DIR);
        if (!arch.exists()) arch.mkdirs();
    }

    @Override
    public void run() {
        while (true) {
            try {
                // 1. Wait before checking again (e.g., every 1 minute for testing)
                Thread.sleep(60000);
                
                System.out.println("Compactor: Checking for cold files...");
                performCompaction();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void performCompaction() throws IOException {
        // 2. Identify Cold Files
        File dir = new File(ACTIVE_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".data"));
        if (files == null || files.length <= 1) return; // Need at least 2 files to compact

        // Sort them and pick all except the last one (the active one)
        Arrays.sort(files, Comparator.comparing(File::getName));
        List<File> coldFiles = new ArrayList<>(Arrays.asList(files));
        coldFiles.remove(coldFiles.size() - 1); 
        
        Set<String> coldFileNames = coldFiles.stream()
                .map(File::getName)
                .collect(Collectors.toSet());

        // 3. Create the new merged archive file
        String mergeId = "merge_" + System.currentTimeMillis();
        File mergeFile = new File(ARCHIVE_DIR, mergeId + ".data");
        File hintFile = new File(ARCHIVE_DIR, mergeId + ".hint");

        try (RandomAccessFile mergeWriter = new RandomAccessFile(mergeFile, "rw");
             DataOutputStream hintWriter = new DataOutputStream(new FileOutputStream(hintFile))) {

            // 4. Loop through the KeyDir (Map-First Strategy)
            for (Map.Entry<String, IndexEntry> item : engine.getKeyDir().entrySet()) {
                String stationId = item.getKey();
                IndexEntry oldEntry = item.getValue();

                // Check if this latest record is currently sitting in a cold file
                if (coldFileNames.contains(oldEntry.fileName)) {
                    moveRecordToArchive(stationId, oldEntry, mergeWriter, hintWriter, mergeFile.getName());
                }
            }
        }

        // 5. Cleanup: Delete the cold files from the active folder
        for (File f : coldFiles) {
            f.delete();
            System.out.println("Compactor: Deleted stale file: " + f.getName());
        }
    }

    private void moveRecordToArchive(String key, IndexEntry oldEntry, RandomAccessFile writer, 
                                     DataOutputStream hintWriter, String newFileName) throws IOException {
        
        // Read the actual JSON from the old file
        String value = readValueFromOldFile(oldEntry);
        if (value == null) return;

        // Write to the NEW archive file
        long newOffset = writer.getFilePointer();
        byte[] keyBytes = key.getBytes();
        byte[] valueBytes = value.getBytes();
        
        writer.writeLong(oldEntry.timestamp);
        writer.writeInt(keyBytes.length);
        writer.writeInt(valueBytes.length);
        writer.write(keyBytes);
        writer.write(valueBytes);

        // Update the IndexEntry to point to the new location
        long newValOffset = newOffset + 8 + 4 + 4 + keyBytes.length;
        IndexEntry newEntry = new IndexEntry(newFileName, newValOffset, valueBytes.length, oldEntry.timestamp);

        // 6. Safe Update: Only update if Kafka hasn't sent a newer update
        engine.updatePointerFromCompactor(key, newEntry, oldEntry);

        // 7. Write to Hint File (For faster startup later)
        // Format: [KeySize][ValueSize][Offset][Timestamp][Key]
        hintWriter.writeInt(keyBytes.length);
        hintWriter.writeInt(valueBytes.length);
        hintWriter.writeLong(newValOffset);
        hintWriter.writeLong(oldEntry.timestamp);
        hintWriter.write(keyBytes);
    }

    private String readValueFromOldFile(IndexEntry entry) {
        String path = ACTIVE_DIR + entry.fileName;
        try (RandomAccessFile reader = new RandomAccessFile(path, "r")) {
            reader.seek(entry.offset);
            byte[] data = new byte[entry.totalSize];
            reader.readFully(data);
            return new String(data);
        } catch (Exception e) {
            return null; // File might have been deleted or corrupted
        }
    }
}
