package javax.ws.rs;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

/**
 * Common error body format expected to be returned in any non-2xx WebApplicationException response.
 */
@Value
@AllArgsConstructor
public class ErrorBody {

    public static ErrorBody get(int code, String message) {
        return new ErrorBody(new Error(code, message));
    }

    @NonNull
    Error error;

    @Value
    @AllArgsConstructor
    public static class Error {
        @NonNull
        int code;
        @NonNull
        String message;
    }
}
