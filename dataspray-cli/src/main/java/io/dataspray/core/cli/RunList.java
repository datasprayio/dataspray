package io.dataspray.core.cli;

import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.Runtime;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import javax.inject.Inject;

@Slf4j
@Command(name = "list", description = "list all deployed versions for task(s)")
public class RunList implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;

    @Inject
    CommandUtil commandUtil;
    @Inject
    Runtime runtime;
    @Inject
    Codegen codegen;
    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        Project project = codegen.loadProject();
        commandUtil.getSelectedTaskIds(project, taskId).forEach(selectedTaskId ->
                runtime.listVersions(cliConfig.getDataSprayApiKey(), project, selectedTaskId));
    }
}
