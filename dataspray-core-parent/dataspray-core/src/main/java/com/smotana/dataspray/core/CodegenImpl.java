package com.smotana.dataspray.core;

import com.google.inject.Inject;
import com.smotana.dataspray.core.definition.model.JavaProcessor;
import com.smotana.dataspray.core.definition.parser.DefinitionLoader;
import org.apache.commons.io.Charsets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;

public class CodegenImpl implements Codegen {
    @Inject
    DefinitionLoader definitionLoader;

    @Override
    public void initProject(Project project) throws IOException {
        File projectDir = Paths.get(".", project.getDefinition().getName()).toFile();
        if (!projectDir.mkdir()) {
            throw new IOException("Folder already exists: " + projectDir.getPath());
        }

        File dsProjectFile = Paths.get(projectDir.getPath(), "ds_project.yml").toFile();
        if (!dsProjectFile.createNewFile()) {
            throw new IOException("File already exists: " + dsProjectFile.getPath());
        }
        try (FileOutputStream fos = new FileOutputStream(dsProjectFile)) {
            fos.write(definitionLoader.toYaml(project.getDefinition()).getBytes(Charsets.UTF_8));
        }
    }

    @Override
    public void generateJava(Project project, JavaProcessor.Dialect dialect) {

    }
}
