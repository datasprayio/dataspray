package com.smotana.dataspray.core;

import com.google.inject.Inject;
import com.smotana.dataspray.core.definition.parser.DefinitionLoaderImpl;
import com.smotana.dataspray.core.definition.parser.DefinitionValidatorImpl;
import com.smotana.dataspray.core.sample.SampleProject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
public class CodegenTest extends CoreAbstractTest {

    @Inject
    private Codegen codegen;

    private Path workingDir;

    @BeforeEach
    public void setupBefore() throws IOException {
        workingDir = Files.createTempDirectory(UUID.randomUUID().toString());
        log.info("WorkingDir: {}", workingDir);
    }

    @Override
    protected void configure() {
        super.configure();

        install(DefinitionLoaderImpl.module());
        install(DefinitionValidatorImpl.module());

        install(CodegenImpl.module());
    }

    @Test
    public void test() throws Exception {
        Project project = codegen.initProject(workingDir.toString(), "test", SampleProject.CLOUD);
        codegen.generateAll(project);
        codegen.installAll(project);
    }
}
