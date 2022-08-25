package io.dataspray.core;

import com.google.inject.Inject;
import io.dataspray.core.definition.parser.DefinitionLoaderImpl;
import io.dataspray.core.sample.SampleProject;
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
public class CodegenBuilderTest extends CoreAbstractTest {
    @Inject
    private Codegen codegen;
    @Inject
    private Builder builder;

    private static MockInOutErr mockInOutErr = new MockInOutErr();
    private Path workingDir;

    @Override
    protected void configure() {
        super.configure();

        install(mockInOutErr.module());

        install(DefinitionLoaderImpl.module());
        install(GitExcludeFileTracker.module());
        install(CodegenImpl.module());
        install(BuilderImpl.module(false));
    }

    @BeforeEach
    public void setupBefore() throws IOException {
        log.info("CWD: {}", System.getProperty("user.dir"));
        if (Path.of(System.getProperty("user.dir"), "target").toFile().isDirectory()) {
            workingDir = Path.of(System.getProperty("user.dir"), "target", "codegen-tests");
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

    @Test
    public void test() throws Exception {
        Project project = codegen.initProject(workingDir.toString(), "Test Project", SampleProject.CLOUD);
        codegen.generateAll(project);
        builder.installAll(project);
        log.info("And again");
        codegen.generateAll(project);
        builder.installAll(project);
    }
}
