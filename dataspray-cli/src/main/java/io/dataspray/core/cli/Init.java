package io.dataspray.core.cli;

import io.dataspray.core.Core;
import io.dataspray.core.sample.SampleProject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;

@Command(name = "init",
        description = "initialize a new project")
public class Init implements Runnable {

    @Parameters(paramLabel = "NAME", description = "Project name")
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
