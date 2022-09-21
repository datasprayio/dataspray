package io.dataspray.core.cli;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@Command(name = "login",
        description = "Set your API key")
public class Login implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Option(names = {"-a", "--apiKey"}, description = "DataSpray API Key")
    String apiKey;

    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        cliConfig.setDataSprayApiKey(Optional.ofNullable(Strings.emptyToNull(this.apiKey))
                .or(() -> Optional.ofNullable(System.console().readPassword("Enter value for --apiKey (DataSpray API Key): "))
                        .map(String::valueOf)
                        .filter(Predicate.not(String::isBlank)))
                .orElseThrow(() -> new RuntimeException("Need to supply api key")));
    }
}
