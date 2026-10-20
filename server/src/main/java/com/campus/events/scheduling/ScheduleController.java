package com.campus.events.scheduling;

import com.campus.events.contract.*;
import com.campus.events.foundation.Responses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activities/{id}")
public class ScheduleController {
    private final ScheduleQueryService queries;
    public ScheduleController(ScheduleQueryService queries) { this.queries = queries; }
    @PostMapping("/schedule-preview") public ResponseEntity<Object> preview(@AuthenticationPrincipal Actor actor,
            @PathVariable UUID id, @Valid @RequestBody Proposal input) {
        return Responses.http(Responses.success(200, queries.preview(actor, id, input, false)));
    }
    @PostMapping("/alternatives") public ResponseEntity<Object> alternatives(@AuthenticationPrincipal Actor actor,
            @PathVariable UUID id, @Valid @RequestBody Proposal input) {
        return Responses.http(Responses.success(200, queries.preview(actor, id, input, true)));
    }
}
