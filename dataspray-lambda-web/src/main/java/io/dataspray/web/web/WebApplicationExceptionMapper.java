/*
 * Copyright 2023 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.web.web;

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
