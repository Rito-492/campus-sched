package com.campus.events.auth;

import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserService users;
    private final CommandExecutor commands;
    private final CanonicalJson json;
    public UserController(UserService users, CommandExecutor commands, CanonicalJson json) { this.users = users; this.commands = commands; this.json = json; }
    @GetMapping("/users") public ResponseEntity<Object> list(@AuthenticationPrincipal Actor actor, @RequestParam(required = false) String purpose) {
        return Responses.http(users.list(actor, purpose));
    }
    @GetMapping("/organizations") public ResponseEntity<Object> organizations(@AuthenticationPrincipal Actor actor) {
        return Responses.http(users.organizations(actor));
    }
    @PostMapping("/users") public ResponseEntity<Object> create(@AuthenticationPrincipal Actor actor,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody UserCreate input) {
        return Responses.http(commands.execute(actor, key, "POST /api/v1/users", json.write(input), () -> Responses.success(201, users.create(actor, input))));
    }
    @PatchMapping("/users/{id}/enabled") public ResponseEntity<Object> enabled(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody EnabledInput input) {
        return Responses.http(commands.execute(actor, key, "PATCH /api/v1/users/" + id + "/enabled", json.write(input), () -> {
            users.enabled(actor, id, input.enabled()); return Responses.success(200, Map.of("ok", true));
        }));
    }
    @PatchMapping("/users/{id}/authorization") public ResponseEntity<Object> authorization(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody AuthorizationInput input) {
        return Responses.http(commands.execute(actor, key, "PATCH /api/v1/users/" + id + "/authorization", json.write(input), () -> Responses.success(200, users.replace(actor, id, input))));
    }
}
