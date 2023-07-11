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

package io.dataspray.core.cli;

import com.google.gson.Gson;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ErrorBody;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@ApplicationScoped
public class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    @Inject
    Gson gson;

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) throws Exception {
        if (LoggingMixin.getIsVerbose()) {
            log.error("Unexpected error:", ex);
        } else {
            Optional<String> webMessageOpt = Optional.empty();
            if (ex instanceof io.dataspray.stream.ingest.client.ApiException) {
                webMessageOpt = Optional.of(((io.dataspray.stream.ingest.client.ApiException) ex).getResponseBody());
            } else if (ex instanceof io.dataspray.stream.control.client.ApiException) {
                webMessageOpt = Optional.of(((io.dataspray.stream.control.client.ApiException) ex).getResponseBody());
            }
            webMessageOpt = webMessageOpt.flatMap(body -> {
                try {
                    ErrorBody errorBody = gson.fromJson(body, ErrorBody.class);
                    return Optional.of(errorBody.getError().getCode() + " " + errorBody.getError().getMessage());
                } catch (Exception ex2) {
                    return Optional.empty();
                }
            });

            log.error("{}", webMessageOpt
                    .orElseGet(() -> Optional.ofNullable(ex.getMessage())
                            .filter(Predicate.not(String::isBlank))
                            .orElse("Unknown error")));
            log.info("Re-run using -v to see more detail");
        }
        return 1;
    }
}
