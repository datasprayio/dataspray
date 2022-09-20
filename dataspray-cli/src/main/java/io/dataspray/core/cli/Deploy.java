package io.dataspray.core.cli;

import io.dataspray.core.Core;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;
import java.io.FileNotFoundException;

@Slf4j
@Command(name = "deploy",
        description = "deploy tasks")
public class Deploy implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Parameters(arity = "0..1", paramLabel = "<task_id>", description = "task id to deploy")
    private String taskId;

    @Inject
    Core core;
    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        if (taskId == null) {
            core.deploy(cliConfig.getDataSprayApiKey());
        } else {
            try {
                core.deploy(cliConfig.getDataSprayApiKey(), taskId);
            } catch (FileNotFoundException ex) {
                log.error(ex.getMessage());
                System.exit(1);
            }
        }
    }
}
