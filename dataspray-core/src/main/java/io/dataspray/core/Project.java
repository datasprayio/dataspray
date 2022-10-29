package io.dataspray.core;

import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.model.Processor;
import lombok.NonNull;
import lombok.Value;
import org.eclipse.jgit.api.Git;

import java.nio.file.Path;
import java.util.Optional;

@Value
public class Project {
    @NonNull
    Path path;
    @NonNull
    Git git;
    @NonNull
    Definition definition;
    /** If set, cwd points to this processor */
    @NonNull
    Optional<String> activeProcessor;

    public Processor getProcessorByName(String name) {
        return getDefinition().getProcessors().stream()
                .filter(p -> p.getName().equals(name))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Processor not found: " + name));
    }
}
