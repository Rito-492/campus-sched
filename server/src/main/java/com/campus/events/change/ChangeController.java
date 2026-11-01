package com.campus.events.change;

import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** API-26～33：改期预览、创建、列表、详情、确认、审批、资源复核与撤回。 */
@RestController
@RequestMapping("/api/v1")
public class ChangeController {
    private final ChangeService changes;
    private final CommandExecutor commands;
    private final CanonicalJson json;

    public ChangeController(ChangeService changes, CommandExecutor commands, CanonicalJson json) {
        this.changes = changes;
        this.commands = commands;
        this.json = json;
    }

    private ResponseEntity<Object> write(Actor actor, UUID key, String method, String path,
                                         Object input, int status, Supplier<Object> action) {
        return Responses.http(commands.execute(actor, key, method + "/api/v1" + path, json.write(input),
                () -> Responses.success(status, action.get())));
    }

    /** 资源复核直接返回业务的完整信封（200 或 409 RESOURCE_CONFLICT），不经二次包装。 */
    private ResponseEntity<Object> decision(Actor actor, UUID key, String method, String path,
                                            Object input, Supplier<CommandResult> action) {
        return Responses.http(commands.execute(actor, key, method + "/api/v1" + path, json.write(input), action));
    }

    @PostMapping("/activities/{id}/change-preview")
    public ResponseEntity<Object> preview(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                          @Valid @RequestBody Proposal input) {
        return Responses.http(Responses.success(200, changes.preview(actor, id, input)));
    }

    @PostMapping("/activities/{id}/changes")
    public ResponseEntity<Object> create(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                         @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody ChangeCreate input) {
        return write(actor, key, "POST", "/activities/" + id + "/changes", input, 201, () -> changes.create(actor, id, input));
    }

    @GetMapping("/activities/{id}/changes")
    public ResponseEntity<Object> list(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return Responses.http(changes.list(actor, id));
    }

    @GetMapping("/changes/{id}")
    public ResponseEntity<Object> get(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return Responses.http(Responses.success(200, changes.get(actor, id)));
    }

    @PostMapping("/changes/{id}/confirmation")
    public ResponseEntity<Object> confirm(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                          @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody ChangeDecision input) {
        return write(actor, key, "POST", "/changes/" + id + "/confirmation", input, 200, () -> changes.confirm(actor, id, input));
    }

    @PostMapping("/changes/{id}/approver-decision")
    public ResponseEntity<Object> approve(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                          @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody ChangeDecision input) {
        return write(actor, key, "POST", "/changes/" + id + "/approver-decision", input, 200, () -> changes.approve(actor, id, input));
    }

    @PostMapping("/changes/{id}/resource-decision")
    public ResponseEntity<Object> resourceDecision(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                                   @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody ChangeDecision input) {
        return decision(actor, key, "POST", "/changes/" + id + "/resource-decision", input, () -> changes.resourceDecision(actor, id, input));
    }

    @PostMapping("/changes/{id}/withdraw")
    public ResponseEntity<Object> withdraw(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                           @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody WithdrawInput input) {
        return write(actor, key, "POST", "/changes/" + id + "/withdraw", input, 200, () -> changes.withdraw(actor, id, input));
    }
}
