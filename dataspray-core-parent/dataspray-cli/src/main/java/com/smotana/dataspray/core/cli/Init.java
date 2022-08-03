package com.smotana.dataspray.core.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.smotana.dataspray.core.Core;
import com.smotana.dataspray.core.sample.SampleProject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "init",
        description = "initialize a new project")
public class Init implements Runnable {

    @Option(required = true, names = "-n", description = "project name")
    private String name;

    @Option(names = "-s", defaultValue = "SampleProject.EMPTY", description = "Sample template: ${COMPLETION-CANDIDATES}")
    private SampleProject sample;

    @Inject
    private Core core;

    @Override
    public void run() {
        core.init(name, sample);
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Init.class).asEagerSingleton();
            }
        };
    }
}
