package com.campus.events.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalJsonTest {
    private final CanonicalJson json = new CanonicalJson(new ObjectMapper());
    @Test void objectOrderAndWhitespaceDoNotChangeHash() {
        assertEquals(json.normalize("{\"a\":1,\"b\":{\"x\":2,\"y\":3}}"), json.normalize("{ \"b\":{\"y\":3,\"x\":2}, \"a\":1 }"));
    }
    @Test void equivalentNumbersHaveSameRepresentation() {
        assertEquals(json.normalize("{\"a\":1000,\"b\":1.0}"), json.normalize("{\"b\":1,\"a\":1e3}"));
    }
    @Test void arraysKeepSemanticOrder() { assertNotEquals(json.normalize("[1,2]"), json.normalize("[2,1]")); }
    @Test void duplicateKeysAndMalformedJsonFail() {
        assertThrows(ApiException.class, () -> json.normalize("{\"a\":1,\"a\":2}"));
        assertThrows(ApiException.class, () -> json.normalize("{"));
        assertThrows(ApiException.class, () -> json.normalize(""));
    }
    @Test void sha256IsUtf8AndStable() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", CanonicalJson.sha256("abc"));
        assertEquals(64, CanonicalJson.sha256("中文").length());
    }
    @Test void preciseDecimalsRemainDistinctAndTrailingValuesFail() {
        assertNotEquals(json.normalize("{\"value\":1.00000000000000001}"), json.normalize("{\"value\":1.00000000000000002}"));
        assertThrows(ApiException.class, () -> json.normalize("{} {}"));
    }
}
