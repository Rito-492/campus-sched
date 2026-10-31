package com.campus.events.notification;

import com.campus.events.contract.Actor;
import com.campus.events.foundation.CanonicalJson;
import com.campus.events.foundation.CommandExecutor;
import com.campus.events.foundation.Responses;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** API-34/35/36：本人通知列表、读取通知、活动审计。 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationService notifications;
    private final CommandExecutor commands;
    private final CanonicalJson json;

    public NotificationController(NotificationService notifications, CommandExecutor commands, CanonicalJson json) {
        this.notifications = notifications;
        this.commands = commands;
        this.json = json;
    }

    @GetMapping("/notifications")
    public ResponseEntity<Object> list(@AuthenticationPrincipal Actor actor,
                                       @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Responses.http(notifications.list(actor, unreadOnly));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Object> read(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                       @RequestHeader("Idempotency-Key") UUID key) {
        return Responses.http(commands.execute(actor, key, "POST /api/v1/notifications/" + id + "/read",
                json.write(Map.of()), () -> Responses.success(200, notifications.read(actor, id))));
    }

    @GetMapping("/activities/{id}/audit")
    public ResponseEntity<Object> audit(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return Responses.http(notifications.audit(actor, id));
    }
}
