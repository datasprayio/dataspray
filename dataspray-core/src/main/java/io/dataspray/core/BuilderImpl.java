package io.dataspray.core;

import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
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
