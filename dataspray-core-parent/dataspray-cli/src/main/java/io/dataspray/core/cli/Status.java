package io.dataspray.core.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import io.dataspray.core.Core;
import picocli.CommandLine.Command;

@Command(name = "status",
        description = "check status of all tasks")
public class Status implements Runnable {

    @Inject
    private Core core;

    @Override
    public void run() {
        core.status();
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Status.class).asEagerSingleton();
            }
        };
    }
}
