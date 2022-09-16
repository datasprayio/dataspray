package io.dataspray.core.cli;

import io.dataspray.core.Core;
import io.dataspray.core.sample.SampleProject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;

@Slf4j
@Command(name = "init",
        description = "initialize a new project")
public class Init implements Runnable {
    @CommandLine.Mixin
    LoggingMixin loggingMixin;

    @Parameters(paramLabel = "NAME", description = "Project name", arity = "1")
    private String name;

    @Option(names = {"-s", "--sample"}, defaultValue = "EMPTY", description = "Sample template: ${COMPLETION-CANDIDATES}")
    private SampleProject sample = SampleProject.EMPTY;

    @Inject
    Core core;

    @Override
    public void run() {
        core.init(name, sample);
    }
}
