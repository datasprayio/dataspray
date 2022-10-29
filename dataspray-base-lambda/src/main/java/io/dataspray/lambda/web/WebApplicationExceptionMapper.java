package io.dataspray.lambda.web;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.ErrorBody;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


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
