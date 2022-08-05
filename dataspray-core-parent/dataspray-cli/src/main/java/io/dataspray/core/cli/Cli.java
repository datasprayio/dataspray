package io.dataspray.core.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(subcommands = {
        Init.class,
        Install.class,
        Deploy.class,
        Status.class,
})
public class Cli {
    @Spec
    CommandSpec spec;

    public static int mainWithExitCode(String[] args) {
        return new CommandLine(
                CliInjector.INSTANCE.get().getInstance(Cli.class),
                CliInjector.INSTANCE.get()::getInstance)
                .execute(args);
    }

    public static void main(String[] args) {
        System.exit(mainWithExitCode(args));
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Cli.class).asEagerSingleton();
            }
        };
    }
}