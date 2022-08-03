package com.smotana.dataspray.core;

public interface Builder {
    void installAll(Project project);

    void installJava(Project project, String processorName);
}
