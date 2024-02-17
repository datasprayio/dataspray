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
import io.dataspray.core.Builder;
import io.dataspray.core.Builder.Artifact;
import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.StreamRuntime;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.Optional;

@Slf4j
@Command(/* Debugging use only */ hidden = true, name = "publish", description = "Upload and publish a new version of a task ")
public class RunPublish implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;
    @Option(names = "--skip-activate", description = "publish without activating version; use activate command to start using the deployed version")
    boolean skipActivate;
    @Option(names = {"-o", "--organization"}, description = "Organization name")
    private String organizationName;

    @Inject
    CommandUtil commandUtil;
    @Inject
    StreamRuntime streamRuntime;
    @Inject
    Codegen codegen;
    @Inject
    Builder builder;
    @Inject
    CliConfig cliConfig;

    @Override
    public void run() {
        Project project = codegen.loadProject();
        commandUtil.getSelectedTaskIds(project, taskId).forEach(selectedTaskId -> {
            Artifact artifact = builder.getBuiltArtifact(project, selectedTaskId)
                    .orElseThrow(() -> new RuntimeException("No artifact for " + selectedTaskId + ", build project first"));
            String codeUrl = streamRuntime.upload(cliConfig.getOrganization(Optional.ofNullable(Strings.emptyToNull(organizationName))), project, selectedTaskId, artifact.getCodeZipFile());
            streamRuntime.publish(cliConfig.getOrganization(Optional.ofNullable(Strings.emptyToNull(organizationName))), project, selectedTaskId, codeUrl, !skipActivate);
        });
    }
}
