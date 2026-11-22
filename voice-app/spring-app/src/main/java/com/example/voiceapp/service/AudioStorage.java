package com.example.voiceapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

/** Permanent folder for recordings. Only bare file names are stored in the database. */
@Component
public class AudioStorage {
    private final Path dir;

    public AudioStorage(@Value("${app.audio-dir}") String d) throws IOException {
        this.dir = Files.createDirectories(Path.of(d).toAbsolutePath().normalize());
    }

    public Path create(String ext) throws IOException { return Files.createTempFile(dir, "rec-", ext); }

    public Path resolve(String name) {
        Path p = dir.resolve(name).normalize();
        if (!p.startsWith(dir)) throw new IllegalArgumentException("Bad file name");
        return p;
    }

    public void delete(String name) {
        try { Files.deleteIfExists(resolve(name)); } catch (IOException ignored) {}
    }

    /** Delete files that no history row references and that are older than minAge. Returns the count. */
    public int deleteOrphans(Set<String> referenced, Duration minAge) throws IOException {
        Instant cutoff = Instant.now().minus(minAge);
        int deleted = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(f) || referenced.contains(f.getFileName().toString())) continue;
                if (Files.getLastModifiedTime(f).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(f);
                    deleted++;
                }
            }
        }
        return deleted;
    }
}
