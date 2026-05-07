package com.khalwsh.chat_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// no auth required — kept outside the Nginx-protected paths so probes can hit it directly
@RestController
@RequestMapping("/api/chat")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
