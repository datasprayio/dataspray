package com.smotana.dataspray.core;

import com.smotana.dataspray.core.sample.SampleProject;

import java.io.IOException;

public interface Codegen {
    Project initProject(String projectName, SampleProject sample) throws IOException;

    void generateAll(Project project);

    void generateJava(Project project, String processorName);
}