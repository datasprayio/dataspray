package io.dataspray.core.cli;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@ApplicationScoped
public class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {
    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) throws Exception {
        if (LoggingMixin.getIsVerbose()) {
            log.error("Unexpected error:", ex);
        } else {
            log.error("{}", Optional.ofNullable(ex.getMessage())
                    .filter(Predicate.not(String::isBlank))
                    .orElse("Unknown error"));
            log.info("Re-run using -v to see more detail");
        }
        return 1;
    }
}
