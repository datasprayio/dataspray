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

package io.dataspray.core;

import com.google.common.collect.ImmutableSet;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import io.dataspray.core.definition.model.TypescriptProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class BuilderImpl implements Builder {
    public static final String BUILDER_IN = "builder.in";
    public static final String BUILDER_OUT = "builder.out";
    public static final String BUILDER_ERR = "builder.err";

    private static final String MVN_CLEAN_INSTALL_CMD = "mvn clean install -e";
    private static final String NPM_INSTALL_BUILD_CMD = "npm run install-and-build";

    @ConfigProperty(name = BUILDER_IN)
    Optional<String> in;
    @ConfigProperty(name = BUILDER_OUT)
    Optional<String> out;
    @ConfigProperty(name = BUILDER_ERR)
    Optional<String> err;

    @Override
    public ImmutableSet<Artifact> buildAll(Project project) {
        return project.getDefinition()
                .getProcessors().stream()
                .map(processor -> build(project, processor))
                .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public Artifact build(Project project, String processorName) {
        return build(project, project.getProcessorByName(processorName));
    }

    @SneakyThrows
    private Artifact build(Project project, Processor processor) {
        String cmd;
        if (processor instanceof JavaProcessor) {
            cmd = MVN_CLEAN_INSTALL_CMD;
        } else if (processor instanceof TypescriptProcessor) {
            cmd = NPM_INSTALL_BUILD_CMD;
        } else {
            throw new RuntimeException("Building processor " + processor.getName() + " type " + processor.getClass().getCanonicalName() + "not supported");
        }

        // Build project
        build(project, processor, cmd);

        // Fetch and verify artifact is created
        Path artifactPath = getArtifactPath(project, processor);
        return getBuiltArtifact(project, processor, artifactPath)
                .orElseThrow(() -> new RuntimeException("Building did not produce an artifact at: " + artifactPath));
    }

    @SneakyThrows
    private void build(Project project, Processor processor, String cmd) {

        ProcessBuilder processBuilder = isWindows()
                ? new ProcessBuilder("cmd.exe", "/c", cmd)
                : new ProcessBuilder("sh", "-c", cmd);
        processBuilder.directory(project.getProcessorDir(processor).toFile());

        err.map(File::new).ifPresentOrElse(processBuilder::redirectError, () -> processBuilder.redirectError(Redirect.INHERIT));
        in.map(File::new).ifPresentOrElse(processBuilder::redirectInput, () -> processBuilder.redirectInput(Redirect.INHERIT));
        out.map(File::new).ifPresentOrElse(processBuilder::redirectOutput, () -> processBuilder.redirectOutput(Redirect.INHERIT));

        log.info("Executing {}", cmd);
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Process exited with non-zero status " + exitCode);
        }
    }

    @Override
    public Optional<Artifact> getBuiltArtifact(Project project, String processorName) {
        return getBuiltArtifact(project, project.getProcessorByName(processorName));
    }

    private Optional<Artifact> getBuiltArtifact(Project project, Processor processor) {
        return getBuiltArtifact(project, processor, getArtifactPath(project, processor));
    }

    private Optional<Artifact> getBuiltArtifact(Project project, Processor processor, Path artifactPath) {
        File codeZipFile = artifactPath.toFile();
        if (!codeZipFile.exists()) {
            return Optional.empty();
        }
        if (!codeZipFile.isFile()) {
            throw new RuntimeException("Expected artifact location is not a regular file: " + codeZipFile.getAbsolutePath());
        }
        return Optional.of(new Artifact(processor, codeZipFile));
    }

    private Path getArtifactPath(Project project, Processor processor) {
        Path codeZipPath;
        if (processor instanceof JavaProcessor) {
            codeZipPath = project.getProcessorDir(processor)
                    .resolve(Path.of("target", processor.getTaskId() + ".jar"));
        } else if (processor instanceof TypescriptProcessor) {
            codeZipPath = project.getProcessorDir(processor)
                    .resolve(Path.of("dist", "index.zip"));
        } else {
            throw new RuntimeException("Cannot build processor " + processor.getName() + " of type " + processor.getClass().getCanonicalName());
        }
        return codeZipPath;
    }

    private boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
    }
}
