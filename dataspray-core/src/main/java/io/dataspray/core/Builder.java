package io.dataspray.core;

public interface Builder {
    void installAll(Project project);

    void install(Project project, String processorName);
}
