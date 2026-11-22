package com.example.voiceapp.repo;

import java.time.LocalDateTime;

/** Projection for the history list: skips the large analysis_json column. */
public interface RecordingSummary {
    Long getId();
    LocalDateTime getCreatedAt();
    String getEditedText();
    double getTotalTime();
    double getSpeakingTime();
    double getSilenceTime();
    int getFilledPauses();
}
