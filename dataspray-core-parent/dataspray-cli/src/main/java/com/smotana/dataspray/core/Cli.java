package com.smotana.dataspray.core;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(subcommands = {
        Install.class,
})
public class Cli {
    @Spec
    CommandSpec spec;

    public static int mainWithExitCode(String[] args) {
        return new CommandLine(new Cli()).execute(args);
    }

    public static void main(String[] args) {
        System.exit(mainWithExitCode(args));
    }
}