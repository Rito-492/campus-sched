package com.campus.events.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;

class ResponsesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void responseKeepsRequestIdAndDoesNotWrapTwice() {
        MDC.put("requestId", "first-request");
        try {
            var result = Responses.success(201, Map.of("ok", true));
            assertEquals(201, Responses.http(result).getStatusCode().value());
            var body = mapper.valueToTree(Responses.http(result).getBody());
            assertEquals("first-request", body.path("requestId").asText());
            assertTrue(body.path("data").path("ok").asBoolean());
            assertFalse(body.path("data").has("data"));
        } finally { MDC.clear(); }
    }
    @Test void errorAndTruncationHaveDedicatedEnvelopes() {
        var error = mapper.valueToTree(Responses.error(409, "RESOURCE_CONFLICT", "冲突", Map.of("conflicts", List.of())).body());
        assertFalse(error.has("data")); assertTrue(error.path("error").has("details"));
        assertTrue(mapper.valueToTree(Responses.list(List.of(1), true).body()).path("truncated").asBoolean());
    }
}
