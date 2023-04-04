package io.dataspray.core.cli;

import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.sample.SampleProject;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Slf4j
@Command(name = "init",
        description = "initialize a new project")
public class Init implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Parameters(paramLabel = "NAME", description = "Project name", arity = "1")
    private String name;

    @Option(names = {"-s", "--sample"}, defaultValue = "EMPTY", description = "Sample template: ${COMPLETION-CANDIDATES}")
    private SampleProject sample = SampleProject.EMPTY;

    @Inject
    Codegen codegen;

    @Override
    public void run() {
        Project project = codegen.initProject(".", name, sample);
        codegen.generateAll(project);
    }
}
