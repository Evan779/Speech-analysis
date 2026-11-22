package com.example.voiceapp.service;

import com.example.voiceapp.repo.RecordingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;

/**
 * Hourly sweep for audio nobody can reach any more: recordings abandoned when a session expired,
 * and files left behind when a user's rows were removed by ON DELETE CASCADE.
 * The 24 h grace period keeps in-progress recordings safe.
 */
@Component
public class AudioCleanup {
    private static final Logger log = LoggerFactory.getLogger(AudioCleanup.class);
    private final AudioStorage storage;
    private final RecordingRepository recordings;

    public AudioCleanup(AudioStorage storage, RecordingRepository recordings) {
        this.storage = storage; this.recordings = recordings;
    }

    @Scheduled(initialDelay = 60_000, fixedRate = 3_600_000)
    public void sweep() {
        try {
            int n = storage.deleteOrphans(new HashSet<>(recordings.findAllAudioFiles()), Duration.ofHours(24));
            if (n > 0) log.info("Removed {} orphaned audio file(s)", n);
        } catch (IOException e) {
            log.warn("Audio cleanup failed", e);
        }
    }
}
