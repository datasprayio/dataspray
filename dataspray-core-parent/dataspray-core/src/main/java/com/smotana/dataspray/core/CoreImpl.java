package com.smotana.dataspray.core;

import com.smotana.dataspray.core.sample.SampleProject;

import java.io.File;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkArgument;

public class CoreImpl implements Core {

    @Override
    public void init(String projectName, SampleProject sample) {
        checkArgument(projectName.matches("^[a-zA-Z0-9-_.]$"), "Project name can only contain: A-Z a-z 0-9 - _ .");
        File projectDir = Paths.get(".", projectName).toFile();
        projectDir.mkdir();

    }

    @Override
    public void install() {

    }

    @Override
    public void deploy() {

    }
}