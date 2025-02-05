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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.MoreExecutors;
import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.StreamRuntime;
import io.dataspray.core.StreamRuntime.Organization;
import io.dataspray.core.definition.model.Processor;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Slf4j
@Command(name = "upload-schema", description = "Upload schema")
public class UploadSchema implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy schema for; otherwise all used schemas are deployed if ran from root directory or specific task schema if ran from within a task directory")
    private String taskId;
    @Option(names = "--no-parallel", description = "deploy serially")
    boolean noParallel;
    @Option(names = {"-p", "--profile"}, description = "Profile name")
    private String profileName;

    @Inject
    CommandUtil commandUtil;
    @Inject
    StreamRuntime streamRuntime;
    @Inject
    Codegen codegen;
    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        Project project = codegen.loadProject();
        List<CompletableFuture<Void>> uploadSchemaTasks = Lists.newArrayList();
        ImmutableSet<String> selectedTaskIds = commandUtil.getSelectedTaskIds(project, taskId);
        Organization organization = cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName)));
        try (var executor = (noParallel ? MoreExecutors.newDirectExecutorService() : Executors.newVirtualThreadPerTaskExecutor())) {
            selectedTaskIds.stream()
                    .map(project::getProcessorByName)
                    .map(Processor::getStreams)
                    .flatMap(List::stream)
                    .distinct()
                    .forEach(streamLink ->
                            uploadSchemaTasks.add(CompletableFuture.supplyAsync(() -> {
                                try {
                                    streamRuntime.uploadSchema(organization, project, streamLink);
                                } catch (Exception ex) {
                                    log.error("Failed to upload schema for task {} store {} stream {} format {}",
                                            streamLink.getParent().getName(),
                                            streamLink.getStoreName(),
                                            streamLink.getStreamName(),
                                            streamLink.getDataFormat().getName(),
                                            ex);
                                }
                                return null;
                            })));
        }

        // Wait for all to complete
        CompletableFuture.allOf(Streams.concat(
                uploadSchemaTasks.stream()
        ).toArray(CompletableFuture[]::new)).join();
    }
}
