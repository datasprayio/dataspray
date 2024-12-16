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
import io.dataspray.core.Codegen;
import io.dataspray.core.Project;
import io.dataspray.core.StreamRuntime;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@Command(/* Debugging use only */ hidden = true, name = "upload", description = "first step of deploy command; prefer to use deploy instead; uploads task code")
public class RunUpload implements Runnable {
    @Mixin
    LoggingMixin loggingMixin;
    @Option(names = {"-t", "--task"}, paramLabel = "<task_id>", description = "specify task id to deploy; otherwise all tasks are used if ran from root directory or specific task if ran from within a task directory")
    private String taskId;
    @Parameters(arity = "1", paramLabel = "<file>", description = "file to upload as runnable code")
    private String file;
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
        File codeFile = new File(file);
        checkState(codeFile.exists(), "Path %s doesn't exist", file);
        checkState(codeFile.isFile(), "Path %s is not a file", file);
        commandUtil.getSelectedTaskIds(project, taskId).forEach(selectedTaskId ->
                streamRuntime.upload(cliConfig.getProfile(Optional.ofNullable(Strings.emptyToNull(profileName))), project, selectedTaskId, codeFile));
    }
}
