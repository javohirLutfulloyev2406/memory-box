package memory.box.memorybox.common.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Uniform error body returned by every failed API call. {@code fieldErrors} is only
 * populated for request validation failures.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Map<String, String>> fieldErrors
) {

    public ApiErrorResponse(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, List.of());
    }

    public ApiErrorResponse(int status, String error, String message, String path, List<Map<String, String>> fieldErrors) {
        this(Instant.now(), status, error, message, path, fieldErrors);
    }
}
