package com.campus.events.resource;

import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {
    private final ResourceService resources;
    private final CommandExecutor commands;
    private final CanonicalJson json;
    public ResourceController(ResourceService resources, CommandExecutor commands, CanonicalJson json) {
        this.resources = resources; this.commands = commands; this.json = json;
    }
    @GetMapping public ResponseEntity<Object> list(@AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) ResourceKind kind, @RequestParam(required = false) String category) {
        return Responses.http(resources.list(actor, kind, category));
    }
    @PostMapping public ResponseEntity<Object> create(@AuthenticationPrincipal Actor actor,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody ResourceCreate input) {
        return Responses.http(commands.execute(actor, key, "POST /api/v1/resources", json.write(input),
                () -> Responses.success(201, resources.create(actor, input))));
    }
    @GetMapping("/calendar") public ResponseEntity<Object> calendar(@AuthenticationPrincipal Actor actor,
            @RequestParam Instant from, @RequestParam Instant to, @RequestParam(required = false) UUID resourceId) {
        return Responses.http(resources.calendar(actor, from, to, resourceId));
    }
    @GetMapping("/{id}/faults") public ResponseEntity<Object> faults(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @RequestParam Instant from, @RequestParam Instant to) {
        return Responses.http(resources.faults(actor, id, from, to));
    }
    @PostMapping("/{id}/faults") public ResponseEntity<Object> fault(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody FaultInput input) {
        return Responses.http(commands.execute(actor, key, "POST /api/v1/resources/" + id + "/faults", json.write(input),
                () -> Responses.success(201, resources.fault(actor, id, input))));
    }
    @PatchMapping("/{id}/eligibility") public ResponseEntity<Object> eligibility(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody ResourceEligibility input) {
        return Responses.http(commands.execute(actor, key, "PATCH /api/v1/resources/" + id + "/eligibility", json.write(input),
                () -> Responses.success(200, resources.eligibility(actor, id, input))));
    }
}
