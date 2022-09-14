package io.dataspray.core.cli;

import io.dataspray.core.Core;
import picocli.CommandLine.Command;

import javax.inject.Inject;

@Command(name = "status",
        description = "check status of all tasks")
public class Status implements Runnable {

    @Inject
    Core core;

    @Override
    public void run() {
        core.status();
    }
}
