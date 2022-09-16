package io.dataspray.core.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@TopCommand
@Command(name = "dst", subcommands = {
        Init.class,
        Install.class,
        Deploy.class,
        Status.class,
})
public class Cli {
    @CommandLine.Mixin
    LoggingMixin loggingMixin;

    @Spec
    CommandSpec spec;
}