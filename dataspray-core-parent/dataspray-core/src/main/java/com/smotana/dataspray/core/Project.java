package com.smotana.dataspray.core;

import com.smotana.dataspray.core.definition.model.Definition;
import lombok.NonNull;
import lombok.Value;
import org.eclipse.jgit.api.Git;

import java.nio.file.Path;

@Value
public class Project {
    @NonNull
    Path path;
    @NonNull
    Git git;
    @NonNull
    Definition definition;
}
