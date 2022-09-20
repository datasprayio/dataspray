package io.dataspray.core.cli;

import io.dataspray.core.Core;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import javax.inject.Inject;

@Command(name = "status",
        description = "check status of all tasks")
public class Status implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Inject
    Core core;
    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        core.status(cliConfig.getDataSprayApiKey());
    }
}
