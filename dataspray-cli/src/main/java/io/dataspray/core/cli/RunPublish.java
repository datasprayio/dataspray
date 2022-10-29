package io.dataspray.core.cli;

import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.Runtime;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;

@Slf4j
@Command(/* Debugging use only */ hidden = true, name = "publish", description = "second step of deploy command; prefer to use deploy instead; publish new version of task with previously uploaded code")
public class RunPublish implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;
    @Option(names = "--skip-activate", description = "publish without activating version; use activate command to start using the deployed version")
    boolean skipActivate;
    @Parameters(arity = "1", paramLabel = "<code_url>", description = "code url to publish returned from running the upload command")
    private String codeUrl;

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
                runtime.publish(cliConfig.getDataSprayApiKey(), project, selectedTaskId, codeUrl, !skipActivate));
    }
}
