package io.dataspray.core.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import io.dataspray.core.Core;
import picocli.CommandLine.Command;

@Command(name = "install",
        description = "compile and install component(s)")
public class Install implements Runnable {

    @Inject
    private Core core;

    @Override
    public void run() {
        core.install();
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Install.class).asEagerSingleton();
            }
        };
    }
}
