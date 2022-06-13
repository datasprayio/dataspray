package com.smotana.dataspray.core;

import com.google.inject.Inject;
import com.smotana.dataspray.core.sample.SampleProject;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

public class CoreImpl implements Core {
    @Inject
    Codegen codegen;

    @Override
    public void init(String projectName, SampleProject sample) throws IOException {
        checkArgument(projectName.matches("^[a-zA-Z0-9-_.]$"), "Project name can only contain: A-Z a-z 0-9 - _ .");

        Project project = codegen.initProject(projectName, sample);

        codegen.generateAll(project);
    }

    @Override
    public void install() {

    }

    @Override
    public void status() {

    }

    @Override
    public void deploy() {

    }
}