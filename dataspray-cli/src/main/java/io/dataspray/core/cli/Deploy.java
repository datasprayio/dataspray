package io.dataspray.core.cli;

import io.dataspray.core.Core;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;

@Command(name = "deploy",
        description = "deploy tasks")
public class Deploy implements Runnable {

    @Parameters(arity = "1", paramLabel = "<task_id>", description = "task id to deploy")
    private String taskId;

    @Inject
    Core core;

    @Override
    public void run() {
        if (taskId == null) {
            core.deploy();
        } else {
            core.deploy(taskId);
        }
    }
}
