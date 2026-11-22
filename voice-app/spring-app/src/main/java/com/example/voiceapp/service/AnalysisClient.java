package com.example.voiceapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

/** Thin client for the Python FastAPI audio service. */
@Component
public class AnalysisClient {
    private final RestClient rest;

    public AnalysisClient(@Value("${app.python.base-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(300_000); // Whisper can be slow on CPU
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public JsonNode transcribe(Path audio) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(audio));
        return post("/transcribe", body);
    }

    public JsonNode analyze(Path audio, String wordsJson, String transcript) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(audio));
        body.add("words", wordsJson == null ? "[]" : wordsJson);
        body.add("transcript", transcript == null ? "" : transcript);
        return post("/analyze", body);
    }

    private JsonNode post(String uri, MultiValueMap<String, Object> body) {
        return rest.post().uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(JsonNode.class);
    }
}
