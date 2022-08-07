package io.dataspray.core;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import io.dataspray.core.sample.SampleProject;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class CoreImpl implements Core {
    @Inject
    Codegen codegen;
    @Inject
    Builder builder;
    @Inject
    Runtime runtime;

    @Override
    public void init(String projectName, SampleProject sample) {
        checkArgument(projectName.matches("^[a-zA-Z0-9-_.]$"), "Project name can only contain: A-Z a-z 0-9 - _ .");

        Project project = codegen.initProject(".", projectName, sample);

        codegen.generateAll(project);
    }

    @Override
    public void install() {
        Project project = codegen.loadProject(".");

        builder.installAll(project);
    }

    @Override
    public void status() {
        Project project = codegen.loadProject(".");

        runtime.statusAll(project);
    }

    @Override
    public void deploy() {
        deploy(Optional.empty());
    }

    @Override
    public void deploy(String processorName) {
        deploy(Optional.of(processorName));
    }

    private void deploy(Optional<String> filterProcessorNameOpt) {
        Project project = codegen.loadProject(".");

        project.getDefinition().getJavaProcessors().stream()
                .filter(processor -> filterProcessorNameOpt.isEmpty() || filterProcessorNameOpt.get().equals(processor.getName()))
                .forEach(processor -> runtime.deploy(project, processor));
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Core.class).to(CoreImpl.class).asEagerSingleton();
            }
        };
    }
}