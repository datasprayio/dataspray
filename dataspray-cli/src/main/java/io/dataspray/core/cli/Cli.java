package io.dataspray.core.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@TopCommand
@Command(subcommands = {
        Init.class,
        Install.class,
        Deploy.class,
        Status.class,
})
public class Cli {
    @Spec
    CommandSpec spec;
}