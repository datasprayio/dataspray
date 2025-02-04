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
@Command(name = "deploy", description = "Single command to publish and activate")
public class Deploy implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;
    @Option(names = "--skip-activate", description = "deploy without activating version; use activate command to start using the deployed version")
    boolean skipActivate;
    @Option(names = "--no-parallel", description = "deploy serially")
    boolean noParallel;
    @Option(names = "--update-schema", description = "also upload schema for input and output streams; allows batch processing to contain latest schema")
    boolean skipSchemaUpdate;
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
        List<CompletableFuture<Void>> deployTasks = Lists.newArrayList();
        List<CompletableFuture<Void>> uploadSchemaTasks = Lists.newArrayList();
        ImmutableSet<String> selectedTaskIds = commandUtil.getSelectedTaskIds(project, taskId);
        Organization organization = cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName)));
        try (var executor = (noParallel ? MoreExecutors.newDirectExecutorService() : Executors.newVirtualThreadPerTaskExecutor())) {

            // Task deployments
            selectedTaskIds.forEach(selectedTaskId -> deployTasks.add(CompletableFuture.supplyAsync(() -> {
                try {
                    streamRuntime.deploy(organization, project, selectedTaskId, !skipActivate);
                } catch (Exception ex) {
                    log.error("Task {} failed to deploy", selectedTaskId, ex);
                }
                return null;
            })));

            // Schema uploads (Applies to all distinct inputs and outputs of all deployed tasks)
            selectedTaskIds.stream()
                    .map(project::getProcessorByName)
                    .map(Processor::getStreams)
                    .flatMap(List::stream)
                    .sorted()
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
                deployTasks.stream(),
                uploadSchemaTasks.stream()
        ).toArray(CompletableFuture[]::new)).join();
    }
}
