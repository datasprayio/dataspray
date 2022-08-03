package com.smotana.dataspray.core.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.smotana.dataspray.core.Core;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "deploy",
        description = "deploy tasks")
public class Deploy implements Runnable {

    @Parameters(arity = "1", paramLabel = "<task_id>", description = "task id to deploy")
    private String taskId;

    @Inject
    private Core core;

    @Override
    public void run() {
        if (taskId == null) {
            core.deploy();
        } else {
            core.deploy(taskId);
        }
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Deploy.class).asEagerSingleton();
            }
        };
    }
}
