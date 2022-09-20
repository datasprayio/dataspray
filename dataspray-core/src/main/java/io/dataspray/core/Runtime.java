package io.dataspray.core;

import io.dataspray.core.definition.model.JavaProcessor;

public interface Runtime {
    void statusAll(String apiKey, Project project);

    void deploy(String apiKey, Project project, JavaProcessor processor);
}
