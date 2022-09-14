package io.dataspray.core;

import io.dataspray.core.definition.model.JavaProcessor;

public interface Runtime {
    void statusAll(Project project);

    void deploy(Project project, JavaProcessor processor);
}