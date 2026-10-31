package com.campus.events.auth;

import com.campus.events.foundation.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    final String random = UUID.randomUUID().toString().replace("-", "");
    @Test void nullAndBlankAreRejected() {
        assertThrows(ApiException.class, () -> PasswordPolicy.validateNew(null));
        assertThrows(ApiException.class, () -> PasswordPolicy.validateNew(" ".repeat(8)));
    }
    @Test void sevenCharactersAreRejected() { assertThrows(ApiException.class, () -> PasswordPolicy.validateNew(random.substring(0, 7))); }
    @Test void eightCharactersAreAccepted() { assertDoesNotThrow(() -> PasswordPolicy.validateNew(random.substring(0, 8))); }
    @Test void bcryptByteBoundaryIsEnforced() {
        assertDoesNotThrow(() -> PasswordPolicy.validateNew(random.repeat(3).substring(0, 72)));
        assertThrows(ApiException.class, () -> PasswordPolicy.validateNew(random.repeat(3).substring(0, 73)));
    }
    @Test void unicodeUsesCharactersForMinimumAndUtf8ForMaximum() {
        assertDoesNotThrow(() -> PasswordPolicy.validateNew("汉".repeat(8)));
        assertThrows(ApiException.class, () -> PasswordPolicy.validateNew("汉".repeat(25)));
    }
    @Test void supplementaryCharactersDoNotCountTwice() {
        assertThrows(ApiException.class, () -> PasswordPolicy.validateNew("😀".repeat(4)));
        assertDoesNotThrow(() -> PasswordPolicy.validateNew("😀".repeat(8)));
    }
}
