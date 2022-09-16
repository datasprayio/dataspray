package io.dataspray.core.cli;

import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import picocli.CommandLine;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

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