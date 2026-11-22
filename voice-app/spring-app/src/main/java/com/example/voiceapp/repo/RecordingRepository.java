package com.example.voiceapp.repo;

import com.example.voiceapp.model.Recording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {
    List<RecordingSummary> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Recording> findByIdAndUserId(Long id, Long userId);   // ownership check built in

    @Query("select r.audioFile from Recording r")
    List<String> findAllAudioFiles();
}
