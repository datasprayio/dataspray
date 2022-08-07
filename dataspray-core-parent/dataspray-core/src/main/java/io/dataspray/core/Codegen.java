package io.dataspray.core;

import io.dataspray.core.sample.SampleProject;

public interface Codegen {
    Project initProject(String basePath, String projectName, SampleProject sample);

    Project loadProject(String projectPath);

    void generateAll(Project project);

    void generateDataFormat(Project project, String dataFormatName);

    void generateJava(Project project, String processorName);
}