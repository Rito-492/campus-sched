package com.campus.events.foundation;

import com.campus.events.contract.CommandResult;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;

public final class Responses {
    private Responses() {}
    public static CommandResult success(int status, Object data) {
        return new CommandResult(status, Map.of("data", data, "requestId", RequestIds.current()));
    }
    public static CommandResult error(int status, String code, String message, Object details) {
        return new CommandResult(status, Map.of("error", Map.of("code", code, "message", message,
                "details", details == null ? Map.of() : details), "requestId", RequestIds.current()));
    }
    public static CommandResult list(List<?> items, boolean truncated) {
        return new CommandResult(200, Map.of("data", items, "truncated", truncated, "requestId", RequestIds.current()));
    }
    public static ResponseEntity<Object> http(CommandResult result) {
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }
}
