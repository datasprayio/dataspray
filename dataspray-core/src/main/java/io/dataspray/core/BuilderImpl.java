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

package io.dataspray.core;

import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.util.Collection;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class BuilderImpl implements Builder {
    public static final String BUILDER_IN = "builder.in";
    public static final String BUILDER_OUT = "builder.out";
    public static final String BUILDER_ERR = "builder.err";

    @ConfigProperty(name = BUILDER_IN)
    Optional<String> in;
    @ConfigProperty(name = BUILDER_OUT)
    Optional<String> out;
    @ConfigProperty(name = BUILDER_ERR)
    Optional<String> err;

    @Override
    public void installAll(Project project) {
        Optional.ofNullable(project.getDefinition()
                        .getJavaProcessors())
                .stream()
                .flatMap(Collection::stream)
                .map(Processor::getName)
                .forEach(processorName -> install(project, processorName));
    }

    @SneakyThrows
    @Override
    public void install(Project project, String processorName) {
        JavaProcessor processor = Optional.ofNullable(project.getDefinition()
                        .getJavaProcessors())
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getName().equals(processorName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find java processor with name " + processorName));

        ProcessBuilder processBuilder = isWindows()
                ? new ProcessBuilder("cmd.exe", "/c", "mvn clean install")
                : new ProcessBuilder("sh", "-c", "mvn clean install");
        processBuilder.directory(CodegenImpl.getProcessorDir(project, processor.getNameDir()).toFile());

        err.map(File::new).ifPresentOrElse(processBuilder::redirectError, () -> processBuilder.redirectError(Redirect.INHERIT));
        in.map(File::new).ifPresentOrElse(processBuilder::redirectInput, () -> processBuilder.redirectInput(Redirect.INHERIT));
        out.map(File::new).ifPresentOrElse(processBuilder::redirectOutput, () -> processBuilder.redirectOutput(Redirect.INHERIT));
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Process exited with non-zero status " + exitCode);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
    }
}
