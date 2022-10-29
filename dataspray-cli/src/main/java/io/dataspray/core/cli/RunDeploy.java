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
@Command(name = "deploy", description = "Single command to upload, publish and switch")
public class RunDeploy implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;
    @Option(names = "--skip-activate", description = "deploy without activating version; use activate command to start using the deployed version")
    boolean skipActivate;

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
                runtime.deploy(cliConfig.getDataSprayApiKey(), project, selectedTaskId, !skipActivate));
    }
}
