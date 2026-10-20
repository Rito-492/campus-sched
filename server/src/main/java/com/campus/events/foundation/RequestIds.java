package com.campus.events.foundation;

import java.util.UUID;
import org.slf4j.MDC;

public final class RequestIds {
    private RequestIds() {}
    public static String current() {
        String value = MDC.get("requestId");
        return value == null ? UUID.randomUUID().toString() : value;
    }
}
