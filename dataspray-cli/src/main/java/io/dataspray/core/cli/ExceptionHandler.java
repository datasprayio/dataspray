package io.dataspray.core.cli;

import com.google.gson.Gson;
import io.dataspray.stream.ingest.client.ApiException;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ErrorBody;
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
                webMessageOpt = Optional.of(((ApiException) ex).getResponseBody());
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
