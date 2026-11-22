package com.example.voiceapp.controller;

import com.example.voiceapp.model.Recording;
import com.example.voiceapp.repo.RecordingRepository;
import com.example.voiceapp.repo.UserRepository;
import com.example.voiceapp.service.AudioStorage;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/** Previous recordings of the logged-in user. Every query is scoped to that user. */
@Controller
public class HistoryController {
    private final RecordingRepository recordings;
    private final UserRepository users;
    private final AudioStorage storage;

    public HistoryController(RecordingRepository recordings, UserRepository users, AudioStorage storage) {
        this.recordings = recordings; this.users = users; this.storage = storage;
    }

    private Long uid(Principal p) { return users.findByUsername(p.getName()).orElseThrow().getId(); }

    private Recording owned(Long id, Principal p) {
        return recordings.findByIdAndUserId(id, uid(p)).orElseThrow(() -> new NotFound());
    }

    @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    static class NotFound extends RuntimeException {}

    @GetMapping("/history")
    public String list(Model m, Principal p) {
        m.addAttribute("items", recordings.findTop100ByUserIdOrderByCreatedAtDesc(uid(p)));
        return "history";
    }

    @GetMapping("/history/{id}")
    public String detail(@PathVariable Long id, Model m, Principal p) {
        m.addAttribute("item", owned(id, p));
        return "history-detail";
    }

    @GetMapping("/api/history/{id}/analysis")
    @ResponseBody
    public ResponseEntity<String> analysis(@PathVariable Long id, Principal p) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(owned(id, p).getAnalysisJson());
    }

    @GetMapping("/api/history/{id}/audio")
    public ResponseEntity<Resource> audio(@PathVariable Long id, Principal p) {
        Recording r = owned(id, p);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(r.getAudioMime()))
                .body(new FileSystemResource(storage.resolve(r.getAudioFile())));
    }

    @PostMapping("/history/{id}/delete")
    public String delete(@PathVariable Long id, Principal p, HttpSession s) {
        Recording r = owned(id, p);
        if (id.equals(s.getAttribute("recordingId"))) VoiceController.resetWorkflow(s);
        storage.delete(r.getAudioFile());
        recordings.delete(r);
        return "redirect:/history?deleted";
    }
}
