package com.campus.events.activity;

import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {
    private final ActivityService activities;
    private final CommandExecutor commands;
    private final CanonicalJson json;
    public ActivityController(ActivityService activities,CommandExecutor commands,CanonicalJson json) { this.activities=activities; this.commands=commands; this.json=json; }
    private ResponseEntity<Object> write(Actor actor,UUID key,String method,String path,Object input,int status,Supplier<Object> action) {
        return Responses.http(commands.execute(actor,key,method+" /api/v1/activities"+path,json.write(input),()->Responses.success(status,action.get())));
    }
    @PostMapping public ResponseEntity<Object> create(@AuthenticationPrincipal Actor actor,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody ActivityInput input) {
        return write(actor,key,"POST","",input,201,()->activities.create(actor,input));
    }
    @GetMapping public ResponseEntity<Object> list(@AuthenticationPrincipal Actor actor,@RequestParam(defaultValue="mine") String scope,@RequestParam(required=false) ActivityStatus status) {
        return Responses.http(activities.list(actor,scope,status));
    }
    @GetMapping("/{id}") public ResponseEntity<Object> get(@AuthenticationPrincipal Actor actor,@PathVariable UUID id) { return Responses.http(Responses.success(200,activities.get(actor,id))); }
    @PatchMapping("/{id}") public ResponseEntity<Object> edit(@AuthenticationPrincipal Actor actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody ActivityEdit input) {
        return write(actor,key,"PATCH","/"+id,input,200,()->activities.edit(actor,id,input));
    }
    @PostMapping("/{id}/submit") public ResponseEntity<Object> submit(@AuthenticationPrincipal Actor actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody VersionCommand input) {
        return write(actor,key,"POST","/"+id+"/submit",input,200,()->activities.submit(actor,id,input));
    }
    @PostMapping("/{id}/withdraw") public ResponseEntity<Object> withdraw(@AuthenticationPrincipal Actor actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody ReasonCommand input) {
        return write(actor,key,"POST","/"+id+"/withdraw",input,200,()->activities.withdraw(actor,id,input));
    }
    @PostMapping("/{id}/approver-decision") public ResponseEntity<Object> decide(@AuthenticationPrincipal Actor actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody ApprovalCommand input) {
        return write(actor,key,"POST","/"+id+"/approver-decision",input,200,()->activities.decide(actor,id,input));
    }
    @PostMapping("/{id}/resource-confirm") public ResponseEntity<Object> confirm(@AuthenticationPrincipal Actor actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody VersionCommand input) {
        return write(actor,key,"POST","/"+id+"/resource-confirm",input,200,()->activities.confirm(actor,id,input));
    }
    @PostMapping("/{id}/publish") public ResponseEntity<Object> publish(@AuthenticationPrincipal Actor actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody VersionCommand input) {
        return write(actor,key,"POST","/"+id+"/publish",input,200,()->activities.publish(actor,id,input));
    }
    @PostMapping("/{id}/cancel") public ResponseEntity<Object> cancel(@AuthenticationPrincipal Actor actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody ReasonCommand input) {
        return write(actor,key,"POST","/"+id+"/cancel",input,200,()->activities.cancel(actor,id,input));
    }
    @PutMapping("/{id}/registration") public ResponseEntity<Object> registration(@AuthenticationPrincipal Actor actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody RegistrationCommand input) {
        return write(actor,key,"PUT","/"+id+"/registration",input,200,()->activities.registration(actor,id,input));
    }
    @GetMapping("/{id}/registrations") public ResponseEntity<Object> registrations(@AuthenticationPrincipal Actor actor,@PathVariable UUID id) { return Responses.http(activities.registrations(actor,id)); }
}
