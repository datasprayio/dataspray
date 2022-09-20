package io.dataspray.core.cli;

import io.dataspray.core.Core;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import javax.inject.Inject;

@Command(name = "install",
        description = "compile and install component(s)")
public class Install implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Inject
    Core core;

    @Override
    public void run() {
        core.install();
    }
}
