package com.smotana.dataspray.core;

import com.smotana.dataspray.core.definition.model.JavaProcessor;

public interface Runtime {
    void statusAll(Project project);

    void deploy(Project project, JavaProcessor processor);
}
