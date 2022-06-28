package com.smotana.dataspray.core;

import com.google.inject.Inject;
import com.smotana.dataspray.core.definition.parser.DefinitionLoaderImpl;
import com.smotana.dataspray.core.definition.parser.DefinitionValidatorImpl;
import com.smotana.dataspray.core.sample.SampleProject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
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

    private static MockInOutErr mockInOutErr = new MockInOutErr();
    private Path workingDir;

    @BeforeEach
    public void setupBefore() throws IOException {
        log.info("CWD: {}", System.getProperty("user.dir"));
        if (Path.of(System.getProperty("user.dir"), "target").toFile().isDirectory()) {
            workingDir = Path.of(System.getProperty("user.dir"), "target", "sample-project");
            FileUtils.deleteDirectory(workingDir.toFile());
            workingDir.toFile().mkdir();
        } else {
            workingDir = Files.createTempDirectory(UUID.randomUUID().toString());
        }
        log.info("WorkingDir: {}", workingDir);
        mockInOutErr = new MockInOutErr();
    }

    @AfterAll
    public static void cleanupAfterAll() throws IOException {
        mockInOutErr.close();
    }

    @Override
    protected void configure() {
        super.configure();

        install(mockInOutErr.module());

        install(DefinitionLoaderImpl.module());
        install(DefinitionValidatorImpl.module());
        install(CodegenImpl.module(false));
    }

    @Test
    public void test() throws Exception {
        Project project = codegen.initProject(workingDir.toString(), "test", SampleProject.CLOUD);
        codegen.generateAll(project);
        codegen.installAll(project);
    }
}
