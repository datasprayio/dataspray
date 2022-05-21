package com.smotana.dataspray.core;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.DefaultSettings;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class Cli {
    private interface Runner {
        void run();
    }

    private static class RunnerInstall implements Runner {
        @Override
        public void run() {
            Cli.core.install(name);
        }

        @Arg
        public String name;
    }

    protected static Core core = new CoreImpl();

    public static void main(String[] args) {
        int exitCode = mainInternal(args);
        if(exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int mainInternal(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("dataspray")
                .build();
        Subparsers subparsers = parser.addSubparsers()
                .title("subcommands")
                .description("valid subcommands")
                .help("additional help");

        Subparser subparserInstall = subparsers.addParser("install")
                .setDefault("runner", new RunnerInstall());
        subparserInstall.addArgument("--name")
                .action(Arguments.storeConst())
                .help("name of the module to install");
        try {
            Namespace ns = parser.parseArgs(args);
            ((Runner)ns.get("runner")).run();
        } catch (ArgumentParserException ex) {
            parser.handleError(ex);
            return 1;
        }
        return 0;
    }
}