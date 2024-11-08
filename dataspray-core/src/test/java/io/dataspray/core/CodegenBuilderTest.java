/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.core;

import io.dataspray.core.sample.SampleProject;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@QuarkusTest
@TestProfile(MockProcessIo.TestProfile.class)
public class CodegenBuilderTest {
    @Inject
    Codegen codegen;
    @Inject
    Builder builder;
    /** Injected via {@link MockProcessIo#inject(Object)} */
    MockProcessIo mockProcessIo;

    private Path workingDir;

    @SneakyThrows
    public void beforeEach(SampleProject sampleProject) {
        if (Path.of(System.getProperty("user.dir"), "target").toFile().isDirectory()) {
            workingDir = Path.of(System.getProperty("user.dir"), "target", "codegen-tests", sampleProject.name());
            FileUtils.deleteDirectory(workingDir.toFile());
            workingDir.toFile().mkdir();
            log.info("WorkingDir: {}", workingDir);
        } else {
            workingDir = Files.createTempDirectory(CodegenBuilderTest.class.getSimpleName() + "-" + sampleProject.name());
            workingDir.toFile().deleteOnExit();
        }
        log.info("Codegen builder running in dir: {}", workingDir);
        // Init git, otherwise dst will look up the tree and find the dataspray .git repository
        Git.init().setDirectory(workingDir.toFile()).call().close();
    }

    @BeforeEach
    @SneakyThrows
    public void beforeEach() {
        mockProcessIo.beforeEach();
    }

    @AfterEach
    @SneakyThrows
    public void afterEach() {
        mockProcessIo.afterEach();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = SampleProject.class, mode = EnumSource.Mode.INCLUDE, names = {"JAVA", "TYPESCRIPT"})
    @Timeout(value = 300)
    public void test(SampleProject sampleProject) throws Exception {
        beforeEach(sampleProject);
        Project project = codegen.initProject(workingDir.toString(), "Test Project " + sampleProject.name(), sampleProject);
        generateAndBuildAll(project);
        log.info("And again");
        generateAndBuildAll(project);
    }

    private void generateAndBuildAll(Project project) {
        codegen.generateAll(project);
        builder.buildAll(project)
                .forEach(artifact -> log.info("Built artifact {}",
                        project.getAbsolutePath().relativize(artifact.getCodeZipFile().toPath())));
    }
}
