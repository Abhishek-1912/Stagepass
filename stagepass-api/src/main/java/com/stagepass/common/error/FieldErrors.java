package com.stagepass.common.error;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects field-level problems found in service code (required-on-create fields, cross-field rules, submit checks)
 * and throws them together as one 400 {@code VALIDATION_FAILED} with {@code errors[]}.
 */
public final class FieldErrors {

    public record Problem(String field, String message) {
    }

    /** Thrown by {@link #throwIfAny()}; rendered by {@link GlobalExceptionHandler}. */
    public static class InvalidFieldsException extends RuntimeException {

        private final List<Problem> problems;

        public InvalidFieldsException(List<Problem> problems) {
            super("Request validation failed");
            this.problems = List.copyOf(problems);
        }

        public List<Problem> problems() {
            return problems;
        }
    }

    private final List<Problem> problems = new ArrayList<>();

    public static FieldErrors start() {
        return new FieldErrors();
    }

    public FieldErrors add(String field, String message) {
        problems.add(new Problem(field, message));
        return this;
    }

    public FieldErrors addIf(boolean condition, String field, String message) {
        if (condition) {
            add(field, message);
        }
        return this;
    }

    public FieldErrors require(Object value, String field) {
        return addIf(value == null || (value instanceof String s && s.isBlank()), field, "is required");
    }

    public void throwIfAny() {
        if (!problems.isEmpty()) {
            throw new InvalidFieldsException(problems);
        }
    }

    public static InvalidFieldsException single(String field, String message) {
        return new InvalidFieldsException(List.of(new Problem(field, message)));
    }
}
