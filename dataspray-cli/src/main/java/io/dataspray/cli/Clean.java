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

import com.google.common.collect.ImmutableSet;
import io.dataspray.core.Builder.Artifact;
import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.Optional;

@Slf4j
@Command(name = "clean",
        description = "clean all generated template boilerplate files for task(s)")
public class Clean implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;

    @Inject
    Codegen codegen;

    @Override
    public void run() {
        Project project = codegen.loadProject();
        Optional<String> activeProcessor = Optional.ofNullable(taskId).or(project::getActiveProcessor);

        ImmutableSet<Artifact> artifacts;
        if (activeProcessor.isEmpty()) {
            codegen.cleanAll(project);
            log.info("Cleaned for all");
        } else {
            codegen.cleanProcessor(project, activeProcessor.get());
            log.info("Cleaned for {}", activeProcessor.get());
        }
    }
}
