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

import io.dataspray.cli.CliConfig.ConfigState;
import io.dataspray.core.StreamRuntime.Organization;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import java.util.Map.Entry;
import java.util.Optional;

@Slf4j
@Command(name = "env", description = "manage environments and credentials", subcommandsRepeatable = true, subcommands = {
        EnvLogin.class,
})
public class Env implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;

    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {

        ConfigState configState = cliConfig.getConfigState();

        if (configState.getOrganizationByProfileName().isEmpty()) {
            log.info("No saved profiles found");
        } else {
            log.info("Found profiles in {}", configState.getConfigFilePath());
            boolean anyHaveEndpoint = configState.getOrganizationByProfileName().values().stream()
                    .anyMatch(o -> o.getEndpoint().isPresent());

            // Header
            if (anyHaveEndpoint) {
                log.info("{}\t{}\t{}\t{}", "Default", "Profile", "Organization", "Endpoint");
                log.info("---\t---\t---\t---");
            } else {
                log.info("{}\t{}\t{}", "Default", "Profile", "Organization");
                log.info("---\t---\t---");
            }

            // Profiles
            Optional<String> defaultProfileName = configState.getDefaultProfileName();
            for (Entry<String, Organization> profile : configState.getOrganizationByProfileName().entrySet()) {
                boolean isDefault = profile.getValue().getName().equals(defaultProfileName.orElse(null));
                if (profile.getValue().getEndpoint().isPresent()) {
                    log.info("\n{}\t{}\t{}\t{}", isDefault, profile.getKey(), profile.getValue().getName(), profile.getValue().getEndpoint().get());
                } else {
                    log.info("\n{}\t{}\t{}", isDefault, profile.getKey(), profile.getValue().getName());
                }
            }
        }
    }
}
