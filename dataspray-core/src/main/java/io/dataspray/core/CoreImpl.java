package io.dataspray.core;

import io.dataspray.core.sample.SampleProject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@ApplicationScoped
public class CoreImpl implements Core {
    @Inject
    Codegen codegen;
    @Inject
    Builder builder;
    @Inject
    Runtime runtime;

    @Override
    public void init(String projectName, SampleProject sample) {
        checkArgument(projectName.matches("^[a-zA-Z0-9-_.]+$"), "Project name can only contain: A-Z a-z 0-9 - _ .");

        Project project = codegen.initProject(".", projectName, sample);

        codegen.generateAll(project);
    }

    @Override
    public void install() {
        Project project = codegen.loadProject(".");

        codegen.generateAll(project);

        builder.installAll(project);
    }

    @Override
    public void status(String apiKey) {
        Project project = codegen.loadProject(".");

        runtime.statusAll(apiKey, project);
    }

    @Override
    @SneakyThrows
    public void deploy(String apiKey) {
        deploy(apiKey, Optional.empty());
    }

    @Override
    public void deploy(String apiKey, String processorName) throws FileNotFoundException {
        deploy(apiKey, Optional.of(processorName));
    }

    private void deploy(String apiKey, Optional<String> filterProcessorNameOpt) throws FileNotFoundException {
        Project project = codegen.loadProject(".");

        long deployCount = project.getDefinition().getJavaProcessors().stream()
                .filter(processor -> {
                    if (filterProcessorNameOpt.isPresent() && !filterProcessorNameOpt.get().equalsIgnoreCase(processor.getName())) {
                        return false;
                    }
                    runtime.deploy(apiKey, project, processor);
                    return true;
                })
                .count();
        if (filterProcessorNameOpt.isPresent() && deployCount == 0) {
            throw new FileNotFoundException("Could not find processor by name: " + filterProcessorNameOpt.get());
        }
    }
}