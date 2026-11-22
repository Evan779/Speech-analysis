CREATE DATABASE IF NOT EXISTS voiceapp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE voiceapp;

-- Passwords are never stored in plain text: password_hash holds a BCrypt hash (60 chars).
CREATE TABLE IF NOT EXISTS users (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One row per finished recording: transcript(s), full analysis (incl. waveform) and the audio file name.
CREATE TABLE IF NOT EXISTS recordings (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  audio_file    VARCHAR(255) NOT NULL,
  audio_mime    VARCHAR(100) NOT NULL,
  whisper_text  MEDIUMTEXT   NOT NULL,   -- what Whisper originally returned
  edited_text   MEDIUMTEXT   NOT NULL,   -- what the user confirmed
  analysis_json MEDIUMTEXT   NOT NULL,   -- full /analyze response
  total_time    DOUBLE       NOT NULL,
  speaking_time DOUBLE       NOT NULL,
  silence_time  DOUBLE       NOT NULL,
  filled_pauses INT          NOT NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_rec_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_rec_user_created (user_id, created_at)
);
