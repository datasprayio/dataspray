package io.dataspray.core.cli;

import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.StreamRuntime;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;
import java.io.File;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@Command(/* Debugging use only */ hidden = true, name = "upload", description = "first step of deploy command; prefer to use deploy instead; uploads task code")
public class RunUpload implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;
    @Parameters(arity = "1", paramLabel = "<file>", description = "file to upload as runnable code")
    private String file;

    @Inject
    CommandUtil commandUtil;
    @Inject
    StreamRuntime streamRuntime;
    @Inject
    Codegen codegen;
    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        Project project = codegen.loadProject();
        File codeFile = new File(file);
        checkState(codeFile.exists(), "Path %s doesn't exist", file);
        checkState(codeFile.isFile(), "Path %s is not a file", file);
        commandUtil.getSelectedTaskIds(project, taskId).forEach(selectedTaskId ->
                streamRuntime.upload(cliConfig.getDataSprayApiKey(), project, selectedTaskId, codeFile));
    }
}
