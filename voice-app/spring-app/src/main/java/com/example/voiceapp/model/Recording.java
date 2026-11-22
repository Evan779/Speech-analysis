package com.example.voiceapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recordings")
public class Recording {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "audio_file", nullable = false) private String audioFile;
    @Column(name = "audio_mime", nullable = false) private String audioMime;
    @Column(name = "whisper_text", nullable = false, columnDefinition = "MEDIUMTEXT") private String whisperText;
    @Column(name = "edited_text", nullable = false, columnDefinition = "MEDIUMTEXT") private String editedText;
    @Column(name = "analysis_json", nullable = false, columnDefinition = "MEDIUMTEXT") private String analysisJson;
    @Column(name = "total_time", nullable = false) private double totalTime;
    @Column(name = "speaking_time", nullable = false) private double speakingTime;
    @Column(name = "silence_time", nullable = false) private double silenceTime;
    @Column(name = "filled_pauses", nullable = false) private int filledPauses;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public String getAudioFile() { return audioFile; }
    public void setAudioFile(String v) { audioFile = v; }
    public String getAudioMime() { return audioMime; }
    public void setAudioMime(String v) { audioMime = v; }
    public String getWhisperText() { return whisperText; }
    public void setWhisperText(String v) { whisperText = v; }
    public String getEditedText() { return editedText; }
    public void setEditedText(String v) { editedText = v; }
    public String getAnalysisJson() { return analysisJson; }
    public void setAnalysisJson(String v) { analysisJson = v; }
    public double getTotalTime() { return totalTime; }
    public void setTotalTime(double v) { totalTime = v; }
    public double getSpeakingTime() { return speakingTime; }
    public void setSpeakingTime(double v) { speakingTime = v; }
    public double getSilenceTime() { return silenceTime; }
    public void setSilenceTime(double v) { silenceTime = v; }
    public int getFilledPauses() { return filledPauses; }
    public void setFilledPauses(int v) { filledPauses = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
