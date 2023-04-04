package io.dataspray.core.cli;

import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import picocli.CommandLine;

@ApplicationScoped
class CommandConfiguration {
    @Inject
    ExceptionHandler exceptionHandler;

    @Produces
    CommandLine customCommandLine(PicocliCommandLineFactory factory) {
        return factory.create()
                .setExecutionStrategy(LoggingMixin::executionStrategy)
                .setExecutionExceptionHandler(exceptionHandler);
    }
}