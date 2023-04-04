package io.dataspray.lambda.web;

import com.google.common.base.Strings;
import jakarta.ws.rs.ErrorBody;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;


/**
 * Ensures all {@link WebApplicationException}s' message are exposed to the client.
 */
@Slf4j
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<Throwable> {
    public static final String ERROR_MESSAGE_KEY = "error";

    @Override
    public Response toResponse(Throwable th) {
        if (th instanceof WebApplicationException) {
            WebApplicationException ex = (WebApplicationException) th;

            // Put exception message to response body if possible
            if (!ex.getResponse().hasEntity()) {
                return Response.fromResponse(ex.getResponse())
                        .entity(ErrorBody.get(
                                ex.getResponse().getStatus(),
                                Strings.nullToEmpty(ex.getMessage()))).build();
            }

            return ex.getResponse();
        } else {
            log.error("Unknown throwable from resource", th);
            return Response.serverError().build();
        }
    }
}
