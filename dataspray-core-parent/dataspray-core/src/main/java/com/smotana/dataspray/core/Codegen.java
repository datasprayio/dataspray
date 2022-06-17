package com.smotana.dataspray.core;

import com.smotana.dataspray.core.sample.SampleProject;

import java.io.IOException;

public interface Codegen {
    Project initProject(String basePath, String projectName, SampleProject sample) throws IOException;

    void generateAll(Project project);

    void generateJava(Project project, String processorName);

    void installAll(Project project);

    void installJava(Project project, String processorName);
}
