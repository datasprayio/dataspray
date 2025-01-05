/*
 * Copyright 2022 remkop
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Taken from:
 * https://github.com/remkop/picocli/blob/main/picocli-examples/src/main/java/picocli/examples/logging_mixin_advanced/LoggingMixin.java
 */
package io.dataspray.cli;

import lombok.SneakyThrows;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogManager;
import org.jboss.logmanager.formatters.PatternFormatter;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

import java.util.Optional;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;
import static picocli.CommandLine.Spec.Target.MIXEE;

/**
 * This is a mixin that adds a {@code --verbose} option to a command.
 * This class will configure logback, using the specified verbosity:
 * <ul>
 *   <li>(not specified) : INFO level is enabled</li>
 *   <li>{@code -v} : DEBUG level with verbose stack traces, log pattern</li>
 *   <li>{@code -vv} : TRACE level is enabled</li>
 * </ul>
 * <p>
 *   To add the {@code --verbose} option to a command, simply declare a {@code @Mixin}-annotated field with type {@code LoggingMixin}
 *   (if your command is a class), or a {@code @Mixin}-annotated method parameter of type {@code LoggingMixin} if your command
 *   is a {@code @Command}-annotated method.
 * </p>
 * <p>
 *   This mixin can be used on multiple commands, on any level in the command hierarchy.
 * </p>
 * <p>
 *   Make sure that {@link #configureLoggers} is called before executing any command.
 *   This can be accomplished with:
 * </p><pre>
 * public static void main(String... args) {
 *     new CommandLine(new MyApp())
 *             .setExecutionStrategy(LoggingMixin::executionStrategy))
 *             .execute(args);
 * }
 * </pre>
 */
public class LoggingMixin {
    private static final String FULL_FORMAT = "%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p %s%e%n";

    private @Spec(MIXEE)
    CommandSpec mixee; // spec of the command where the @Mixin is used

    private boolean[] verbosity = new boolean[0];

    private static LoggingMixin getTopLevelCommandLoggingMixin(CommandSpec commandSpec) {
        return ((Cli) commandSpec.root().userObject()).loggingMixin;
    }

    @Option(names = {"-v", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity.",
            "For example, `-v -v` or `-vv`"})
    public void setVerbose(boolean[] verbosity) {
        getTopLevelCommandLoggingMixin(mixee).verbosity = verbosity;
    }

    public boolean[] getMixeeVerbosity() {
        return getTopLevelCommandLoggingMixin(mixee).verbosity;
    }

    public static boolean getIsVerbose() {
        return org.slf4j.LoggerFactory.getLogger(ROOT_LOGGER_NAME)
                .isDebugEnabled();
    }

    public static int executionStrategy(ParseResult parseResult) {
        getTopLevelCommandLoggingMixin(parseResult.commandSpec()).configureLoggers();
        return new CommandLine.RunLast().execute(parseResult);
    }

    @SneakyThrows
    public void configureLoggers() {
        int verbosity = getTopLevelCommandLoggingMixin(mixee).getMixeeVerbosity().length;
        Optional<Level> levelOpt;
        Optional<PatternFormatter> formatOpt;
        switch (verbosity) {
            case 0:
                return;
            case 1:
                levelOpt = Optional.of(Level.FINE);
                formatOpt = Optional.of(new PatternFormatter(FULL_FORMAT));
                break;
            default:
                levelOpt = Optional.of(Level.ALL);
                formatOpt = Optional.of(new PatternFormatter(FULL_FORMAT));
                break;
        }

        var logContext = LogContext.getLogContext();
        var rootLogger = logContext.getLogger("");

        for (var handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler consoleHandler) {
                levelOpt.ifPresent(consoleHandler::setLevel);
                formatOpt.ifPresent(consoleHandler::setFormatter);
            }
        }
        LogManager.getLogManager().getLoggerNames().asIterator().forEachRemaining(loggerName -> {
            var logger = logContext.getLogger(loggerName);
            levelOpt.ifPresent(logger::setLevel);
        });
    }
}
