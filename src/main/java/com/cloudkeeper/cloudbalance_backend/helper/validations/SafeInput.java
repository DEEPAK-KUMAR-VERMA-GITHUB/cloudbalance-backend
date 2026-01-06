package com.cloudkeeper.cloudbalance_backend.helper.validations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeInputValidator.class)
@Documented
public @interface SafeInput {
    String message() default "Input contains potentially dangerous content.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
