package com.cloudkeeper.cloudbalance_backend.helper.validations;

public final class ValidationPatterns {

    // prevent object creation
    private ValidationPatterns() {}

    // Basic patterns
    public static final String ALPHANUMERIC = "^[a-zA-Z0-9]+$";
    public static final String ALPHANUMERIC_SPACE = "^[a-zA-Z0-9\\s]+$";
    public static final String ALPHANUMERIC_SPACE_DASH = "^[a-zA-Z0-9\\s\\-_]+$";

    // Name patterns (no HTML/special chars)
    public static final String PERSON_NAME = "^[a-zA-Z\\s'-]+$";

    // Email (strict, blocks XSS)
    public static final String EMAIL = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    // Block HTML tags and dangerous characters
    public static final String NO_HTML = "^[^<>\"'&;]*$";

    // Block script tags, SQL keywords, and common XSS patterns
    public static final String NO_XSS = "^(?!.*(script|iframe|object|embed|onload|onerror|onclick|javascript:|vbscript:|data:|eval|expression|alert|confirm|prompt|select|insert|update|delete|drop|union|exec|declare)).*$";

    // AWS specific
    public static final String AWS_ACCOUNT_ID = "^[0-9]{12}$";
    public static final String AWS_ROLE_ARN = "arn:aws:iam::\\d{12}:role/[\\w+=,.@-]+";
    public static final String AWS_ACCESS_KEY = "^[A-Z0-9]{20}$";
    public static final String AWS_SECRET_KEY = "^[A-Za-z0-9/+=]{40}$";
    public static final String AWS_REGION = "^[a-z]{2}-[a-z]+-[0-9]{1}$";

    // Messages
    public static final String MSG_NO_HTML = "Input contains invalid HTML characters";
    public static final String MSG_NO_XSS = "Input contains potentially malicious content";
    public static final String MSG_ALPHANUMERIC = "Input must contain only letters and numbers";
    public static final String MSG_PERSON_NAME = "Name can only contain letters, spaces, hyphens, and apostrophes";
}
