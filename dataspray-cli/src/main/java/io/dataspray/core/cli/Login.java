package io.dataspray.core.cli;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import javax.inject.Inject;

@Slf4j
@Command(name = "login",
        description = "Set your API key")
public class Login implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Option(names = {"-a", "--apiKey"}, description = "DataSpray API Key", interactive = true)
    String apiKey;

    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        cliConfig.setDataSprayApiKey(apiKey);
    }
}
