package io.dataspray.core;

import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.lang.ProcessBuilder.Redirect;
import java.util.Collection;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class BuilderImpl implements Builder {
    @Inject
    @Named("IN")
    Redirect in;
    @Inject
    @Named("OUT")
    Redirect out;
    @Inject
    @Named("ERR")
    Redirect err;

    @Override
    public void installAll(Project project) {
        Optional.ofNullable(project.getDefinition()
                        .getJavaProcessors())
                .stream()
                .flatMap(Collection::stream)
                .map(Processor::getName)
                .forEach(processorName -> installJava(project, processorName));
    }

    @SneakyThrows
    @Override
    public void installJava(Project project, String processorName) {
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
        processBuilder.redirectError(err);
        processBuilder.redirectInput(in);
        processBuilder.redirectOutput(out);
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

    @Named("IN")
    @ApplicationScoped
    public static ProcessBuilder.Redirect getInput() {
        return Redirect.INHERIT;
    }

    @Named("OUT")
    @ApplicationScoped
    public static ProcessBuilder.Redirect getOutput() {
        return Redirect.INHERIT;
    }

    @Named("ERR")
    @ApplicationScoped
    public static ProcessBuilder.Redirect getError() {
        return Redirect.INHERIT;
    }
}
