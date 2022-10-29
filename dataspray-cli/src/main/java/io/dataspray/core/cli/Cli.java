package io.dataspray.core.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@TopCommand
@Command(name = "dst",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        subcommandsRepeatable = true,
        subcommands = {
                Init.class,
                Install.class,
                Run.class,
                Login.class,
        }
)
public class Cli {
    @Mixin
    LoggingMixin loggingMixin;

    @Spec
    CommandSpec spec;

}