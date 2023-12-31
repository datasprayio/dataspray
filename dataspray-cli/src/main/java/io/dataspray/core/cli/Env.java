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

import io.dataspray.core.StreamRuntime.Organization;
import io.dataspray.core.cli.CliConfig.ConfigState;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

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


        if (configState.getOrganizations().isEmpty()) {
            log.info("No saved environments found");
        } else {
            log.info("Reading environments from: {}", configState.getConfigFilePath());
            boolean anyHaveEndpoint = configState.getOrganizations().stream().anyMatch(o -> o.getEndpoint().isPresent());

            // Header
            if (anyHaveEndpoint) {
                log.info("\n{}\t{}\t{}", "Default", "Organization", "Endpoint");
                log.info("---\t---\t---");
            } else {
                log.info("\n{}\t{}", "Default", "Organization");
                log.info("---\t---");
            }

            // Organizations
            for (Organization organization : configState.getOrganizations()) {
                boolean isDefault = organization.getName().equals(configState.getDefaultOrganization().orElse(null));
                if (organization.getEndpoint().isPresent()) {
                    log.info("\n{}\t{}\t{}", isDefault, organization.getName(), organization.getEndpoint().get());
                } else {
                    log.info("\n{}\t{}", isDefault, organization.getName());
                }
            }
        }
    }
}
