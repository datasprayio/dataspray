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
import io.dataspray.core.Builder;
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
@Command(name = "install",
        description = "compile and install task(s)")
public class Install implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;
    @Option(names = {"-o", "--overwrite-writeable-template"}, description = "force overwrite of template files if they were given writeable permissions; typically this means someone has modified a template file accidentally")
    private boolean overwriteWriteableTemplate;

    @Inject
    Codegen codegen;
    @Inject
    Builder builder;

    @Override
    public void run() {
        Project project = codegen.loadProject();
        Optional<String> activeProcessor = Optional.ofNullable(taskId).or(project::getActiveProcessor);

        ImmutableSet<Artifact> artifacts;
        if (activeProcessor.isEmpty()) {
            codegen.generateAll(project, overwriteWriteableTemplate);
            artifacts = builder.buildAll(project);
        } else {
            codegen.generateProcessor(project, activeProcessor.get(), overwriteWriteableTemplate);
            artifacts = ImmutableSet.of(builder.build(project, activeProcessor.get()));
        }

        artifacts.forEach(artifact -> log.info("Built artifact {}",
                project.getAbsolutePath().relativize(artifact.getCodeZipFile().toPath())));
    }
}
