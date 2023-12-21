/*
 * Copyright 2023 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.core.cli;

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@Command(name = "login",
        description = "Set your API key")
public class Login implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Option(names = {"-o", "--organization"}, description = "Organization name")
    private String organizationName;
    @Option(names = {"-a", "--apiKey"}, description = "API Key")
    private String apiKey;
    @Option(names = {"-d", "--default"}, description = "Set as default")
    private boolean setAsDefault;

    @Inject
    private CliConfig cliConfig;

    @Override
    public void run() {
        String organizationName = Optional.ofNullable(Strings.emptyToNull(this.organizationName))
                .or(() -> Optional.ofNullable(System.console().readLine("Enter value for --organization (Organization name): "))
                        .map(String::trim)
                        .filter(Predicate.not(String::isBlank)))
                .orElseThrow(() -> new RuntimeException("Need to supply organization name"));
        String apiKey = Optional.ofNullable(Strings.emptyToNull(this.apiKey))
                .or(() -> Optional.ofNullable(System.console().readPassword("Enter value for --apiKey (API Key): "))
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(Predicate.not(String::isBlank)))
                .orElseThrow(() -> new RuntimeException("Need to supply api key"));
        cliConfig.setOrganization(organizationName, apiKey);
        if (setAsDefault || cliConfig.getDefaultOrganization().isEmpty()) {
            cliConfig.setDefaultOrganization(organizationName);
        } else {
            boolean setAsDefaultResponse = Optional.ofNullable(System.console().readLine("Set as --default ? (y/n): "))
                    .map(String::trim)
                    .filter("y"::equalsIgnoreCase)
                    .isPresent();
            if (setAsDefaultResponse) {
                cliConfig.setDefaultOrganization(organizationName);
            }
        }
    }
}
