package com.example.voiceapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoiceAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoiceAppApplication.class, args);
    }
}
