package com.campus.events.auth;

import com.campus.events.contract.Actor;
import com.campus.events.contract.LoginInput;
import com.campus.events.foundation.Responses;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final SessionService sessions;
    private final AccountRepository accounts;
    public AuthController(SessionService sessions, AccountRepository accounts) { this.sessions = sessions; this.accounts = accounts; }
    @PostMapping("/login") public ResponseEntity<Object> login(@Valid @RequestBody LoginInput input) {
        return Responses.http(Responses.success(200, sessions.login(input)));
    }
    @GetMapping("/me") public ResponseEntity<Object> me(@AuthenticationPrincipal Actor actor) {
        return Responses.http(Responses.success(200, accounts.get(actor.id(), false)));
    }
    @PostMapping("/logout") public ResponseEntity<Object> logout(@RequestHeader("Authorization") String header) {
        sessions.logout(header.substring(7));
        return Responses.http(Responses.success(200, Map.of("ok", true)));
    }
}
