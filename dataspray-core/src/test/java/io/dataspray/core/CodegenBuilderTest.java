package io.dataspray.core;

import io.dataspray.core.sample.SampleProject;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@QuarkusTest
@TestProfile(MockInOutErr.class)
public class CodegenBuilderTest {
    @Inject
    Codegen codegen;
    @Inject
    Builder builder;
    @Inject
    MockInOutErr mockInOutErr;

    private Path workingDir;

    @BeforeEach
    @SneakyThrows
    public void beforeEach() {
        if (Path.of(System.getProperty("user.dir"), "target").toFile().isDirectory()) {
            workingDir = Path.of(System.getProperty("user.dir"), "target", "codegen-tests");
            FileUtils.deleteDirectory(workingDir.toFile());
            workingDir.toFile().mkdir();
            log.info("WorkingDir: {}", workingDir);
        } else {
            workingDir = Files.createTempDirectory(CodegenBuilderTest.class.getSimpleName());
            workingDir.toFile().deleteOnExit();
        }
    }

    @AfterEach
    @SneakyThrows
    public void afterEach() {
        Files.walk(workingDir)
                .filter(Files::isRegularFile)
                .forEach(p -> log.debug("Working dir file: {}", workingDir.relativize(p)));
        workingDir.toFile().delete();
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
