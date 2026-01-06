package com.cloudkeeper.cloudbalance_backend.helper.validations;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SafeInputValidator implements ConstraintValidator<SafeInput, String> {

    private static final Pattern[] DANGEROUS_PATTERNS = {
            Pattern.compile("<script", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onerror=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onload=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\(", Pattern.CASE_INSENSITIVE),
            // SQL Injection patterns
            Pattern.compile("(union|select|insert|update|delete|drop)\\s+(from|into|table)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("--|;--|\\/\\*|\\*\\/"),// SQL comment syntax
            // SQL Injection patterns - COMPREHENSIVE
            Pattern.compile("('|(\\-\\-)|(;)|(\\|\\|)|(\\*))", Pattern.CASE_INSENSITIVE), // Common SQL chars
            Pattern.compile("\\b(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|eval)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\%27)|(\\')|(\\-\\-)|(\\%23)|(#)"), // URL encoded and comment syntax
            Pattern.compile("(\\;|\\||\\|\\||\\&|\\&\\&)"), // SQL/command separators
            Pattern.compile("(\\*|\\%|\\+|\\<|\\>)") // Wildcards and operators

    };

    /**
     * Implements the validation logic.
     * The state of {@code value} must not be altered.
     * <p>
     * This method can be accessed concurrently, thread-safety must be ensured
     * by the implementation.
     *
     * @param value   object to validate
     * @param context context in which the constraint is evaluated
     * @return {@code false} if {@code value} does not pass the constraint
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        if (value == null || value.isEmpty()) {
            return true;
        }

        // check against all dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return false;   // reject if any dangerous pattern find
            }
        }

        return true;
    }
}
