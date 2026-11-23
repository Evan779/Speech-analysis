package com.example.voiceapp.controller;

import com.example.voiceapp.model.Recording;
import com.example.voiceapp.repo.RecordingRepository;
import com.example.voiceapp.repo.UserRepository;
import com.example.voiceapp.service.AnalysisClient;
import com.example.voiceapp.service.AudioStorage;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Locale;
import java.util.Map;

/**
 * Window 1: /record      Window 2: /transcript      Windows 3+4: /analysis
 * In-progress state lives in the HTTP session; a finished analysis is saved to the recordings table.
 */
@Controller
public class VoiceController {
    private static final String AUDIO = "audioFile", MIME = "audioMime", ORIGINAL = "whisperText",
            TEXT = "transcriptText", WORDS = "wordsJson", ANALYSIS = "analysisJson", REC_ID = "recordingId";

    /** Accepted upload types: extension -> MIME type used for playback. */
    private static final Map<String, String> TYPES = Map.of(
            ".webm", "audio/webm", ".wav", "audio/wav", ".mp3", "audio/mpeg", ".m4a", "audio/mp4",
            ".mp4", "audio/mp4", ".ogg", "audio/ogg", ".oga", "audio/ogg", ".flac", "audio/flac", ".aac", "audio/aac");

    private final AnalysisClient python;
    private final AudioStorage storage;
    private final UserRepository users;
    private final RecordingRepository recordings;

    public VoiceController(AnalysisClient python, AudioStorage storage, UserRepository users, RecordingRepository recordings) {
        this.python = python; this.storage = storage; this.users = users; this.recordings = recordings;
    }

    /** Forget the in-progress workflow (does not touch saved history). */
    static void resetWorkflow(HttpSession s) {
        for (String k : new String[]{AUDIO, MIME, ORIGINAL, TEXT, WORDS, ANALYSIS, REC_ID}) s.removeAttribute(k);
    }

    // ---------- pages ----------
    @GetMapping("/")
    public String home() { return "redirect:/record"; }

    @GetMapping("/record")
    public String record() { return "record"; }

    @GetMapping("/transcript")
    public String transcript(HttpSession s) {
        return s.getAttribute(AUDIO) == null ? "redirect:/record" : "transcript";
    }

    @GetMapping("/analysis")
    public String analysis(HttpSession s) {
        return (s.getAttribute(AUDIO) == null || s.getAttribute(TEXT) == null) ? "redirect:/record" : "analysis";
    }

    // ---------- Window 1: receive the recording ----------
    @PostMapping("/api/recording")
    @ResponseBody
    public ResponseEntity<?> upload(@RequestParam("audio") MultipartFile audio, HttpSession s) throws IOException {
        if (audio.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Empty recording"));

        String name = audio.getOriginalFilename() == null ? "rec.webm" : audio.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : ".webm";
        if (!TYPES.containsKey(ext))
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type. Use MP3, WAV, M4A, WebM, OGG, FLAC or AAC."));

        // An abandoned, never-saved recording is deleted; saved ones belong to the history.
        String old = (String) s.getAttribute(AUDIO);
        if (old != null && s.getAttribute(REC_ID) == null) storage.delete(old);
        resetWorkflow(s);

        Path file = storage.create(ext);
        audio.transferTo(file);

        s.setAttribute(AUDIO, file.getFileName().toString());
        String ct = audio.getContentType();
        s.setAttribute(MIME, ct != null && ct.startsWith("audio/") ? ct : TYPES.get(ext));
        return ResponseEntity.ok(Map.of("redirect", "/transcript"));
    }

    /** Stream the in-progress recording for <audio> playback (supports Range requests). */
    @GetMapping("/api/recording/audio")
    public ResponseEntity<Resource> audio(HttpSession s) {
        String f = (String) s.getAttribute(AUDIO);
        if (f == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String) s.getAttribute(MIME)))
                .body(new FileSystemResource(storage.resolve(f)));
    }

    // ---------- Window 2: transcription ----------
    @PostMapping("/api/transcribe")
    @ResponseBody
    public ResponseEntity<?> transcribe(@RequestParam(defaultValue = "false") boolean force, HttpSession s) {
        String f = (String) s.getAttribute(AUDIO);
        if (f == null) return ResponseEntity.badRequest().body(Map.of("error", "No recording found"));
        if (!force && s.getAttribute(TEXT) != null)
            return ResponseEntity.ok(Map.of("text", s.getAttribute(TEXT)));
        try {
            JsonNode r = python.transcribe(storage.resolve(f));
            String text = r.path("text").asText("");
            s.setAttribute(ORIGINAL, text);
            s.setAttribute(TEXT, text);
            s.setAttribute(WORDS, r.path("words").toString());
            s.removeAttribute(ANALYSIS);
            return ResponseEntity.ok(Map.of("text", text));
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Transcription service unavailable: " + e.getMessage()));
        }
    }

    /** Save the (possibly user-corrected) transcript. */
    @PostMapping("/api/transcript")
    @ResponseBody
    public ResponseEntity<?> saveTranscript(@RequestBody Map<String, String> body, HttpSession s) {
        s.setAttribute(TEXT, body.getOrDefault("text", "").trim());
        s.removeAttribute(ANALYSIS);
        return ResponseEntity.ok(Map.of("redirect", "/analysis"));
    }

    // ---------- Windows 3 + 4: fluency analysis & waveform (saved to history) ----------
    @PostMapping("/api/analyze")
    @ResponseBody
    public ResponseEntity<?> analyze(HttpSession s, Principal who) {
        String f = (String) s.getAttribute(AUDIO);
        if (f == null) return ResponseEntity.badRequest().body(Map.of("error", "No recording found"));
        try {
            Object cached = s.getAttribute(ANALYSIS);
            if (cached != null) return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(cached);

            JsonNode r = python.analyze(storage.resolve(f), (String) s.getAttribute(WORDS), (String) s.getAttribute(TEXT));
            String json = r.toString();
            save(s, who, f, json, r);
            s.setAttribute(ANALYSIS, json);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Analysis service unavailable: " + e.getMessage()));
        }
    }

    /** Insert a history row the first time, update it if the same recording is re-analysed after edits. */
    private void save(HttpSession s, Principal who, String file, String json, JsonNode r) {
        Long userId = users.findByUsername(who.getName()).orElseThrow().getId();
        Long id = (Long) s.getAttribute(REC_ID);
        Recording rec = id == null ? new Recording()
                : recordings.findByIdAndUserId(id, userId).orElseGet(Recording::new);
        rec.setUserId(userId);
        rec.setAudioFile(file);
        rec.setAudioMime((String) s.getAttribute(MIME));
        rec.setWhisperText((String) s.getAttribute(ORIGINAL));
        rec.setEditedText((String) s.getAttribute(TEXT));
        rec.setAnalysisJson(json);
        rec.setTotalTime(r.path("total_time").asDouble());
        rec.setSpeakingTime(r.path("speaking_time").asDouble());
        rec.setSilenceTime(r.path("silence_time").asDouble());
        rec.setFilledPauses(r.path("filled_pauses").path("count").asInt());
        s.setAttribute(REC_ID, recordings.save(rec).getId());
    }
}