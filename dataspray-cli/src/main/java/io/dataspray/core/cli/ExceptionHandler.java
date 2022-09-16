package io.dataspray.core.cli;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import javax.enterprise.context.ApplicationScoped;

@Slf4j
@ApplicationScoped
public class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {
    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) throws Exception {
        log.error("Unexpected error:", ex);
        return 1;
    }
}
