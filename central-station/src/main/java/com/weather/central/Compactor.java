package com.weather.central;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Compactor implements Runnable {
    private final BitCaskEngine engine;
    private final String activeDir;
    private final String archiveDir;

    public Compactor(BitCaskEngine engine, String dataDir) {
        this.engine = engine;
        this.activeDir = dataDir + "/active/";
        this.archiveDir = dataDir + "/archive/";
        File arch = new File(archiveDir);
        if (!arch.exists()) arch.mkdirs();
    }

    @Override
    public void run() {
        while (true) {
            try {
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
        File dir = new File(activeDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".data"));
        if (files == null || files.length <= 1) return;

        Arrays.sort(files, Comparator.comparing(File::getName));
        List<File> coldFiles = new ArrayList<>(Arrays.asList(files));
        coldFiles.remove(coldFiles.size() - 1);

        Set<String> coldFileNames = coldFiles.stream()
                .map(File::getName)
                .collect(Collectors.toSet());

        String mergeId = "merge_" + System.currentTimeMillis();
        File mergeFile = new File(archiveDir, mergeId + ".data");
        File hintFile = new File(archiveDir, mergeId + ".hint");

        try (RandomAccessFile mergeWriter = new RandomAccessFile(mergeFile, "rw");
             DataOutputStream hintWriter = new DataOutputStream(new FileOutputStream(hintFile))) {

            for (Map.Entry<String, IndexEntry> item : engine.getKeyDir().entrySet()) {
                String stationId = item.getKey();
                IndexEntry oldEntry = item.getValue();
                if (coldFileNames.contains(oldEntry.fileName)) {
                    moveRecordToArchive(stationId, oldEntry, mergeWriter, hintWriter, mergeFile.getName());
                }
            }
        }

        for (File f : coldFiles) {
            f.delete();
            System.out.println("Compactor: Deleted stale file: " + f.getName());
        }
    }

    private void moveRecordToArchive(String key, IndexEntry oldEntry, RandomAccessFile writer,
                                     DataOutputStream hintWriter, String newFileName) throws IOException {
        String value = readValueFromOldFile(oldEntry);
        if (value == null) return;

        long newOffset = writer.getFilePointer();
        byte[] keyBytes = key.getBytes();
        byte[] valueBytes = value.getBytes();

        writer.writeLong(oldEntry.timestamp);
        writer.writeInt(keyBytes.length);
        writer.writeInt(valueBytes.length);
        writer.write(keyBytes);
        writer.write(valueBytes);

        long newValOffset = newOffset + 8 + 4 + 4 + keyBytes.length;
        IndexEntry newEntry = new IndexEntry(newFileName, newValOffset, valueBytes.length, oldEntry.timestamp);
        engine.updatePointerFromCompactor(key, newEntry, oldEntry);

        hintWriter.writeInt(keyBytes.length);
        hintWriter.writeInt(valueBytes.length);
        hintWriter.writeLong(newValOffset);
        hintWriter.writeLong(oldEntry.timestamp);
        hintWriter.write(keyBytes);
    }

    private String readValueFromOldFile(IndexEntry entry) {
        String path = activeDir + entry.fileName;
        try (RandomAccessFile reader = new RandomAccessFile(path, "r")) {
            reader.seek(entry.offset);
            byte[] data = new byte[entry.totalSize];
            reader.readFully(data);
            return new String(data);
        } catch (Exception e) {
            return null;
        }
    }
}