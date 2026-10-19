package com.campus.events.foundation;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/v1/health") public ResponseEntity<Object> health() {
        return Responses.http(Responses.success(200, Map.of("status", "UP")));
    }
}
