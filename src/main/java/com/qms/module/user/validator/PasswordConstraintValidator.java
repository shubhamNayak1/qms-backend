package com.qms.module.user.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PasswordConstraintValidator
        implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;

    private static final Pattern UPPERCASE  = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE  = Pattern.compile("[a-z]");
    private static final Pattern DIGIT      = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL    = Pattern.compile("[@#$%^&+=!*()\\-_]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext ctx) {
        if (password == null || password.isBlank()) return false; // @NotBlank handles the message

        List<String> violations = new ArrayList<>();

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            violations.add("must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (!UPPERCASE.matcher(password).find()) {
            violations.add("must contain at least one uppercase letter");
        }
        if (!LOWERCASE.matcher(password).find()) {
            violations.add("must contain at least one lowercase letter");
        }
        if (!DIGIT.matcher(password).find()) {
            violations.add("must contain at least one digit");
        }
        if (!SPECIAL.matcher(password).find()) {
            violations.add("must contain at least one special character (@#$%^&+=!*)");
        }
        if (WHITESPACE.matcher(password).find()) {
            violations.add("must not contain whitespace");
        }

        if (violations.isEmpty()) return true;

        // Build a single, descriptive violation message
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(
                "Password " + String.join("; ", violations))
                .addConstraintViolation();
        return false;
    }
}
