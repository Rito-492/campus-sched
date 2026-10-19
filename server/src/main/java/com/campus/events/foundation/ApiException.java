package com.campus.events.foundation;

import java.util.Map;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Object details;

    public ApiException(int status, String code, String message) { this(status, code, message, Map.of()); }
    public ApiException(int status, String code, String message, Object details) {
        super(message); this.status = status; this.code = code; this.details = details;
    }
    public int status() { return status; }
    public String code() { return code; }
    public Object details() { return details; }
    public static ApiException forbidden() { return new ApiException(403, "FORBIDDEN", "无权执行此操作"); }
    public static ApiException notFound() { return new ApiException(404, "NOT_FOUND", "记录不存在"); }
    public static ApiException invalid(String message) { return new ApiException(400, "VALIDATION_ERROR", message); }
    public static ApiException unauthenticated() { return new ApiException(401, "UNAUTHENTICATED", "请重新登录"); }
}
