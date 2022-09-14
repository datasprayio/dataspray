package io.dataspray.core.cli;

import io.dataspray.core.Core;
import io.dataspray.core.sample.SampleProject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.inject.Inject;

@Command(name = "init",
        description = "initialize a new project")
public class Init implements Runnable {

    @Option(required = true, names = "-n", description = "project name")
    private String name;

    @Option(names = "-s", defaultValue = "SampleProject.EMPTY", description = "Sample template: ${COMPLETION-CANDIDATES}")
    private SampleProject sample;

    @Inject
    Core core;

    @Override
    public void run() {
        core.init(name, sample);
    }
}
