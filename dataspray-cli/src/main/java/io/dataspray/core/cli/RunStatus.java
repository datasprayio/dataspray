package io.dataspray.core.cli;

import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.Runtime;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import javax.inject.Inject;
import java.util.Optional;

@Command(name = "status",
        description = "check status of all tasks")
public class RunStatus implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;

    @Inject
    Codegen codegen;
    @Inject
    Runtime runtime;
    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        Project project = codegen.loadProject();
        Optional<String> activeProcessor = Optional.ofNullable(taskId).or(project::getActiveProcessor);
        if (activeProcessor.isEmpty()) {
            runtime.statusAll(cliConfig.getDataSprayApiKey(), project);
        } else {
            runtime.status(cliConfig.getDataSprayApiKey(), project, activeProcessor.get());
        }
    }
}
