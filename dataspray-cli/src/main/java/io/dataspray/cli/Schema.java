/*
 * Copyright 2025 Matus Faro
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
import io.dataspray.stream.control.client.model.TopicSchema;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Optional;

@Slf4j
@Command(name = "schema",
        description = "Manage topic schemas",
        subcommands = {
                Schema.Update.class,
                Schema.Get.class
        })
public class Schema implements Runnable {

    @Override
    public void run() {
        System.err.println("Use subcommands: update, get");
        System.err.println("Run 'dst schema --help' for more information");
    }

    @Slf4j
    @Command(name = "update",
            description = "Recalculate schema from ingested S3 data")
    static class Update implements Runnable {
        @Mixin
        LoggingMixin loggingMixin;

        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @Option(names = {"-p", "--profile"}, description = "Profile name")
        String profileName;

        @Inject
        CliConfig cliConfig;

        @Override
        public void run() {
            var profile = cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName)));

            log.info("Recalculating schema for topic: {} in organization: {}", topicName, profile.organizationName());

            try {
                TopicSchema schema = profile.controlApi().recalculateTopicSchema(profile.organizationName(), topicName);
                log.info("Schema recalculated successfully:");
                log.info("Format: {}", schema.getFormat());
                log.info("Schema:\n{}", schema.getSchema());
            } catch (Exception ex) {
                log.error("Failed to recalculate schema for topic {}: {}", topicName, ex.getMessage(), ex);
                throw new RuntimeException("Schema recalculation failed", ex);
            }
        }
    }

    @Slf4j
    @Command(name = "get",
            description = "Get current schema for a topic")
    static class Get implements Runnable {
        @Mixin
        LoggingMixin loggingMixin;

        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @Option(names = {"-p", "--profile"}, description = "Profile name")
        String profileName;

        @Inject
        CliConfig cliConfig;

        @Override
        public void run() {
            var profile = cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName)));

            log.info("Fetching schema for topic: {} in organization: {}", topicName, profile.organizationName());

            try {
                TopicSchema schema = profile.controlApi().getTopicSchema(profile.organizationName(), topicName);
                log.info("Current schema:");
                log.info("Format: {}", schema.getFormat());
                log.info("Schema:\n{}", schema.getSchema());
            } catch (Exception ex) {
                log.error("Failed to get schema for topic {}: {}", topicName, ex.getMessage(), ex);
                throw new RuntimeException("Failed to get schema", ex);
            }
        }
    }
}
