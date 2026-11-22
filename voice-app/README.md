# Voice Fluency Lab

Spring Boot (pages, sessions, auth) + MySQL (users) + Python FastAPI (Whisper, librosa, Praat).

```
voice-app/
├── schema.sql                      MySQL schema (users table)
├── python-service/
│   ├── app.py                      FastAPI: POST /transcribe, POST /analyze
│   └── requirements.txt
└── spring-app/
    ├── pom.xml
    └── src/main
        ├── java/com/example/voiceapp/
        │   ├── VoiceAppApplication.java
        │   ├── config/SecurityConfig.java        form login, BCrypt, CSRF
        │   ├── controller/AuthController.java    /login, /register
        │   ├── controller/VoiceController.java   /record, /transcript, /analysis + /api/*
        │   ├── model/AppUser.java, Recording.java, RegisterForm.java
        │   ├── repo/UserRepository.java, RecordingRepository.java
        │   └── service/AppUserDetailsService.java, AnalysisClient.java, AudioStorage.java
        └── resources/
            ├── application.properties
            ├── templates/   fragments, login, register, record, transcript, analysis, history, history-detail
            └── static/      css/style.css, js/{common,record,transcript,analysis}.js
```

## Run it

1. **Database**
   `mysql -u root -p < schema.sql` (safe to re-run; upgrading from the first version just adds the `recordings` table)
2. **Python service** (needs Python 3.9+ and `ffmpeg` on PATH)
   ```
   cd python-service
   python -m venv .venv && source .venv/bin/activate
   pip install -r requirements.txt
   uvicorn app:app --port 8000          # WHISPER_MODEL=small for better accuracy
   ```
3. **Spring Boot** (Java 17+, Maven)
   ```
   cd spring-app
   export DB_USER=root DB_PASSWORD=yourpassword
   mvn spring-boot:run
   ```
4. Open http://localhost:8080 (use `localhost` or HTTPS; browsers block the microphone on plain-HTTP remote hosts).

## Flow

| Step | URL | What happens |
|---|---|---|
| Auth | `/register`, `/login` | BCrypt-hashed password saved in MySQL; login redirects to `/record` |
| Window 1 | `/record` | MediaRecorder, hard stop at 60 s, preview, upload to `/api/recording` |
| Window 2 | `/transcript` | Spring forwards audio to Python `/transcribe` (Whisper). Text shown in a locked box; **Edit text** unlocks it, **Undo my edits** restores Whisper's version |
| Windows 3 + 4 | `/analysis` | Spring forwards audio + word timings + your edited text to Python `/analyze`; metrics and waveform shown together, and the result is **saved to history** |
| History | `/history`, `/history/{id}` | Table of your past recordings; open one to see its transcript (plus Whisper's original if you edited it), metrics, waveform and audio, or delete it |

## How the metrics are computed

- **Silence detection:** Praat intensity contour (parselmouth); frames more than 25 dB below the 95th-percentile intensity are silent. Silent gaps under 0.25 s are treated as normal articulation, not pauses.
- **Total time** = file length. **Speech production** = summed voiced segments. **Silence** = total − speech (includes leading and trailing silence).
- **Filled pauses:** counted in the *edited* transcript (um, uh, er, hmm and variants). Whisper often deletes fillers, so the service prompts it to keep them, and the edit step lets you add any it missed.
- **Gaps between vs within clauses:** your edited transcript is aligned word by word to Whisper's timestamps (matching words keep their real timings; words you added are spread across the gap where they were inserted). Each pause is then matched to the word before it: if that word ends in , . ; : ! ? or a dash, the gap is *between clauses*; otherwise *within a clause*. So fixing or adding punctuation in the edit step changes the result. Without word timings it falls back to "≥ 0.5 s means between".
- **Waveform:** min/max envelope (1,200 bins) from librosa, drawn on a canvas with pauses shaded and a clickable playhead.

Tune with env vars: `MIN_PAUSE` (default 0.25), `SILENCE_DB` (default 25), `WHISPER_MODEL` (default base).

## Housekeeping and remaining limits

- In-progress work is held in the HTTP session (lost on app restart or after 60 minutes idle). Finished recordings are saved to the `recordings` table and their audio to `app.audio-dir` (default `~/voiceapp-audio`, override with `AUDIO_DIR`). Back that folder up with the database.
- An hourly job deletes audio files that no history row references and that are older than 24 hours (abandoned sessions, rows removed by a user-delete cascade).
- The history list loads only summary columns; the full analysis JSON (about 25 KB with waveform) is read when you open one recording.
- Re-analysing the same recording after editing the transcript updates its history entry instead of adding a duplicate.
- Words you add during editing have no measured timing, so their clause placement is an estimate. Pauses and speech/silence totals are always measured from the audio.
- Whisper on CPU can take about a minute for 60 s of audio. Use `WHISPER_MODEL=tiny` for speed or `small` for accuracy.
