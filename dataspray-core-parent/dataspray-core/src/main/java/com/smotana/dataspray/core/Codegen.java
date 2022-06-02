package com.smotana.dataspray.core;

import java.io.IOException;

public interface Codegen {
    void initProject(Project project) throws IOException;

    void generateAllJava(Project project);

    void generateJava(Project project, String processorName);

}