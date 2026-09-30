package com.receipttrust.common.exception;

/**
 * Application exception hierarchy. Each concrete type maps to an HTTP status
 * in {@link GlobalExceptionHandler}.
 */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** 404 */
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    /** 403 */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }

    /** 409 */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** 400 */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /** 413 */
    public static class PayloadTooLargeException extends RuntimeException {
        public PayloadTooLargeException(String message) {
            super(message);
        }
    }

    /** 401 — deliberately carries no field-specific detail. */
    public static class AuthenticationFailure
            extends org.springframework.security.core.AuthenticationException {
        public AuthenticationFailure() {
            super("Authentication failed");
        }
    }

    /**
     * 409 — the account exists but has no local password and must sign in via
     * its social provider (unless the user later sets a password).
     */
    public static class SocialLoginRequired extends ConflictException {
        public SocialLoginRequired(String provider) {
            super("This account uses " + capitalize(provider)
                    + " sign-in. Use that, or set a password from your profile to enable password login.");
        }

        private static String capitalize(String s) {
            if (s == null || s.isEmpty()) {
                return s;
            }
            return s.charAt(0) + s.substring(1).toLowerCase();
        }
    }
}
