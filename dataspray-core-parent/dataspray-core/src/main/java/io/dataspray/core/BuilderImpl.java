package io.dataspray.core;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.ProcessBuilder.Redirect;
import java.util.Collection;
import java.util.Optional;

@Slf4j
public class BuilderImpl implements Builder {
    @Inject
    @Named("IN")
    private Redirect in;
    @Inject
    @Named("OUT")
    private Redirect out;
    @Inject
    @Named("ERR")
    private Redirect err;

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

    public static Module module(boolean useProcessInputOutput) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Builder.class).to(BuilderImpl.class).asEagerSingleton();
                if (useProcessInputOutput) {
                    bind(Redirect.class).annotatedWith(Names.named("IN")).toInstance(Redirect.INHERIT);
                    bind(Redirect.class).annotatedWith(Names.named("OUT")).toInstance(Redirect.INHERIT);
                    bind(Redirect.class).annotatedWith(Names.named("ERR")).toInstance(Redirect.INHERIT);
                }
            }
        };
    }
}
