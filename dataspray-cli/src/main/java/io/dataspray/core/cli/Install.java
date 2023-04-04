package io.dataspray.core.cli;

import io.dataspray.core.Builder;
import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.Optional;

@Command(name = "install",
        description = "compile and install task(s)")
public class Install implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;

    @Inject
    Codegen codegen;
    @Inject
    Builder builder;

    @Override
    public void run() {
        Project project = codegen.loadProject();
        Optional<String> activeProcessor = Optional.ofNullable(taskId).or(project::getActiveProcessor);
        if (activeProcessor.isEmpty()) {
            codegen.generateAll(project);
            builder.installAll(project);
        } else {
            codegen.generate(project, activeProcessor.get());
            builder.install(project, activeProcessor.get());
        }
    }
}
