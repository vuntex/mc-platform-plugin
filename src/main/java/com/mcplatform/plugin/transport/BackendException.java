package com.mcplatform.plugin.transport;

/**
 * Normed error model for backend calls. A failed {@link java.util.concurrent.CompletableFuture}
 * completes exceptionally with one of these, so every feature maps backend failures the same way.
 * Status codes mirror the backend's {@code EconomyExceptionHandler} (see PROGRESS.md):
 *
 * <ul>
 *   <li>422 → {@link InsufficientFunds}</li>
 *   <li>409 → {@link Conflict}</li>
 *   <li>400 → {@link BadRequest}</li>
 *   <li>404 → {@link NotFound}</li>
 *   <li>5xx / other non-2xx / transport failure → {@link BackendError} (the only retryable kind)</li>
 * </ul>
 *
 * The response body (if any) is carried through in {@link #responseBody()} for diagnostics/messages.
 */
public abstract sealed class BackendException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    private BackendException(int statusCode, String responseBody, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /** HTTP status, or {@code 0} for a transport-level failure (timeout/connection). */
    public int statusCode() {
        return statusCode;
    }

    /** Raw response body if the backend sent one, otherwise {@code null}. */
    public String responseBody() {
        return responseBody;
    }

    /** Map a non-2xx status (with optional body) to the matching exception. */
    public static BackendException fromStatus(int status, String body) {
        String suffix = (body == null || body.isBlank()) ? "" : ": " + body;
        return switch (status) {
            case 400 -> new BadRequest(body, "Bad request (400)" + suffix);
            case 404 -> new NotFound(body, "Not found (404)" + suffix);
            case 409 -> new Conflict(body, "Conflict (409)" + suffix);
            case 422 -> new InsufficientFunds(body, "Insufficient funds (422)" + suffix);
            default -> new BackendError(status, body, "Backend error (" + status + ")" + suffix, null);
        };
    }

    /** A transport-level failure (timeout, connection reset). Retryable when the call is idempotent. */
    public static BackendError transportFailure(String message, Throwable cause) {
        return new BackendError(0, null, message, cause);
    }

    public static final class InsufficientFunds extends BackendException {
        private InsufficientFunds(String body, String message) {
            super(422, body, message, null);
        }
    }

    public static final class Conflict extends BackendException {
        private Conflict(String body, String message) {
            super(409, body, message, null);
        }
    }

    public static final class BadRequest extends BackendException {
        private BadRequest(String body, String message) {
            super(400, body, message, null);
        }
    }

    public static final class NotFound extends BackendException {
        private NotFound(String body, String message) {
            super(404, body, message, null);
        }
    }

    public static final class BackendError extends BackendException {
        private BackendError(int statusCode, String body, String message, Throwable cause) {
            super(statusCode, body, message, cause);
        }
    }
}
