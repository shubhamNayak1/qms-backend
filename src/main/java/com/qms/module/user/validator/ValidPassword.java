package com.qms.module.user.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a password meets the QMS complexity requirements:
 *   - Minimum 8, maximum 64 characters
 *   - At least one uppercase letter
 *   - At least one lowercase letter
 *   - At least one digit
 *   - At least one special character  (@#$%^&+=!*)
 *   - No whitespace allowed
 */
@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Password must be 8-64 characters and contain at least one uppercase "
            + "letter, one lowercase letter, one digit, and one special character (@#$%^&+=!*)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
