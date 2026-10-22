package com.analoghub.platform.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
            "status", "UP",
            "message", "AnalogHub backend is running"
        );
    }
}