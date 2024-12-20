/*
 * Copyright 2024 Matus Faro
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

package io.dataspray.cli;

import com.google.common.base.Strings;
import io.dataspray.core.StreamRuntime;
import io.dataspray.stream.control.client.ApiException;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.Optional;
import java.util.function.Predicate;

import static io.dataspray.core.StreamRuntime.Organization;

@Slf4j
@Command(name = "login", description = "Set your environment and credentials")
public class EnvLogin implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Option(names = {"-p", "--profile"}, description = "New profile name")
    private String profileName;
    @Option(names = {"-o", "--organization"}, description = "Organization name")
    private String organizationName;
    @Option(names = {"-a", "--apiKey"}, description = "API Key")
    private String apiKey;
    @Option(names = {"-e", "--endpoint"}, description = "Custom endpoint for self-hosted installations")
    private String endpoint;
    @Option(names = {"-d", "--default"}, description = "Set as default")
    private boolean setAsDefault;
    @Option(names = {"-s", "--skipCheck"}, description = "Skip checking validity of API Key")
    private boolean skipCheck;

    @Inject
    CliConfig cliConfig;
    @Inject
    StreamRuntime streamRuntime;

    @Override
    public void run() {

        // Retrieve profile name
        String profileName = Optional.ofNullable(Strings.emptyToNull(this.profileName))
                .or(() -> Optional.ofNullable(System.console().readLine("Enter value for profile name: "))
                        .map(String::trim)
                        .filter(Predicate.not(String::isBlank)))
                .orElseThrow(() -> new RuntimeException("Need to supply profile name"));

        // Retrieve organization name
        String organizationName = Optional.ofNullable(Strings.emptyToNull(this.organizationName))
                .or(() -> Optional.ofNullable(System.console().readLine("Enter value for organization name: "))
                        .map(String::trim)
                        .filter(Predicate.not(String::isBlank)))
                .orElseThrow(() -> new RuntimeException("Need to supply organization name"));

        // Decide whether org should be set as default
        if (setAsDefault || !cliConfig.hasDefaultProfileName()) {
            cliConfig.setDefaultOrganization(organizationName);
        } else {
            boolean setAsDefaultResponse = Optional.ofNullable(System.console().readLine("Set as default organization? (y/n): "))
                    .map(String::trim)
                    .filter("y"::equalsIgnoreCase)
                    .isPresent();
            if (setAsDefaultResponse) {
                cliConfig.setDefaultOrganization(organizationName);
            }
        }
        // Retrieve api key
        String apiKey = Optional.ofNullable(Strings.emptyToNull(this.apiKey))
                .or(() -> Optional.ofNullable(System.console().readPassword("Enter value for API Key: "))
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(Predicate.not(String::isBlank)))
                .orElseThrow(() -> new RuntimeException("Need to supply api key"));

        // Retrieve endpoint or use default if blank
        Optional<String> endpointOpt = Optional.ofNullable(Strings.emptyToNull(this.endpoint))
                .or(() -> Optional.ofNullable(System.console().readLine("Enter value or leave blank for endpoint (https://api.dataspray.io): "))
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(Predicate.not(String::isBlank)));

        Organization organization = new Organization(organizationName, apiKey, endpointOpt);

        if (!skipCheck) {
            log.info("Checking validity of API Key...");
            try {
                streamRuntime.ping(organization);
                log.info("Successfully verified API Key against server!");
            } catch (ApiException ex) {
                log.error("Failed to verify API against server! ({} {})", ex.getCode(), ex.getMessage(), ex);
                boolean saveAnyway = Optional.ofNullable(System.console().readLine("Save anyway? (y/n): "))
                        .map(String::trim)
                        .filter("y"::equalsIgnoreCase)
                        .isPresent();
                if (!saveAnyway) {
                    return;
                }
            }
        }

        // Write organization to disk
        cliConfig.setProfile(profileName, organization);
        log.info("Wrote profile to disk");
    }
}
