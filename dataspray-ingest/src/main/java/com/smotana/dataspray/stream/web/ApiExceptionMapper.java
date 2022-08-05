package com.smotana.dataspray.stream.web;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.smotana.dataspray.core.common.json.GsonProvider;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Slf4j
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {
    public static final String ERROR_MESSAGE_KEY = "error";

    @Override
    public Response toResponse(Throwable th) {
        if (th instanceof WebApplicationException) {
            WebApplicationException ex = (WebApplicationException) th;

            if (ex instanceof ClientErrorException || ex instanceof ServerErrorException) {
                if (!Strings.isNullOrEmpty(ex.getMessage()) && !ex.getResponse().hasEntity()) {
                    return Response.fromResponse(ex.getResponse())
                            .entity(GsonProvider.getStatic().toJson(ImmutableMap.of(ERROR_MESSAGE_KEY, ex.getMessage())))
                            .build();
                }
            }

            return ex.getResponse();
        } else {
            log.error("Unknown throwable from resource", th);
            return Response.serverError().build();
        }
    }
}
