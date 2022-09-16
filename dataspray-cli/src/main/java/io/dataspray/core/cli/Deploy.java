package io.dataspray.core.cli;

import io.dataspray.core.Core;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;
import java.io.FileNotFoundException;

@Slf4j
@Command(name = "deploy",
        description = "deploy tasks")
public class Deploy implements Runnable {
    @CommandLine.Mixin
    LoggingMixin loggingMixin;

    @Parameters(arity = "0..1", paramLabel = "<task_id>", description = "task id to deploy")
    private String taskId;

    @Inject
    Core core;

    @Override
    public void run() {
        if (taskId == null) {
            core.deploy();
        } else {
            try {
                core.deploy(taskId);
            } catch (FileNotFoundException ex) {
                log.error(ex.getMessage());
                System.exit(1);
            }
        }
    }
}
