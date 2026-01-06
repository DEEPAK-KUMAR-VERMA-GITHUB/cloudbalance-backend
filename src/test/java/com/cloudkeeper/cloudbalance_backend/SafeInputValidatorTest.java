package com.cloudkeeper.cloudbalance_backend;

import com.cloudkeeper.cloudbalance_backend.helper.validations.SafeInputValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeInputValidatorTest {

    private SafeInputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SafeInputValidator();
    }

    @Test
    void testValidInput() {
        assertTrue(validator.isValid("John Doe", null));
        assertTrue(validator.isValid("test@example.com", null));
        assertTrue(validator.isValid("My Account 123", null));
        assertTrue(validator.isValid(null, null)); // null is valid, @NotBlank handles it
        assertTrue(validator.isValid("", null)); // empty is valid
    }

    @Test
    void testXSSAttacks() {
        assertFalse(validator.isValid("<script>alert('XSS')</script>", null));
        assertFalse(validator.isValid("javascript:alert(1)", null));
        assertFalse(validator.isValid("<img src=x onerror=alert(1)>", null));
        assertFalse(validator.isValid("<iframe src='evil.com'></iframe>", null));
        assertFalse(validator.isValid("<object data='evil.com'></object>", null));
        assertFalse(validator.isValid("eval('malicious')", null));
    }

    @Test
    void testSQLInjection() {
        assertFalse(validator.isValid("'; DROP TABLE users--", null));
        assertFalse(validator.isValid("1' UNION SELECT * FROM users", null));
        assertFalse(validator.isValid("admin'--", null));
        assertFalse(validator.isValid("/* comment */ SELECT", null));
    }

    @Test
    void testCaseInsensitive() {
        assertFalse(validator.isValid("<SCRIPT>alert(1)</SCRIPT>", null));
        assertFalse(validator.isValid("JAVASCRIPT:alert(1)", null));
        assertFalse(validator.isValid("OnErRoR=alert(1)", null));
    }
}
