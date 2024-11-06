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

import com.google.common.collect.ImmutableSet;
import io.dataspray.core.sample.SampleProject;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@QuarkusTest
class GitExcludeFileTrackerTest {

    @Inject
    FileTracker fileTracker;

    private Path workingDir;

    @BeforeEach
    @SneakyThrows
    public void beforeEach() {
        workingDir = Files.createTempDirectory(GitExcludeFileTrackerTest.class.getSimpleName());
        workingDir.toFile().deleteOnExit();
    }

    @AfterEach
    @SneakyThrows
    public void afterEach() {
        try (var walk = Files.walk(workingDir)) {
            walk.filter(Files::isRegularFile)
                    .forEach(p -> log.debug("Working dir file: {}", workingDir.relativize(p)));
        }
        workingDir.toFile().delete();
    }

    @Test
    void test() throws Exception {
        Git.init().setDirectory(workingDir.toFile()).call();
        Project project = new Project(workingDir, Git.open(workingDir.toFile()), SampleProject.EMPTY.getDefinitionForName("test"), Optional.empty());

        File file1 = createFile("file1");
        File file2 = createFile("file2");
        File file3 = createFile("dir1/file3");
        File file4 = createFile("dir1/file4");

        assertEquals(ImmutableSet.of(), fileTracker.getTrackedFiles(project, Optional.empty(), Optional.empty()));
        assertEquals(ImmutableSet.of(), fileTracker.getTrackedFiles(project, Optional.of(Path.of("dir1")), Optional.empty()));

        assertTrue(fileTracker.trackFile(project, Path.of("file5")));
        File file5 = createFile("file5");
        assertTrue(fileTracker.trackFile(project, Path.of("file6")));
        File file6 = createFile("file6");
        assertTrue(fileTracker.trackFile(project, Path.of("dir1/file7")));
        File file7 = createFile("dir1/file7");
        assertTrue(fileTracker.trackFile(project, Path.of("dir1/file8")));
        File file8 = createFile("dir1/file8");
        assertTrue(fileTracker.trackFile(project, Path.of("dir2/file9")));
        File file9 = createFile("dir2/file9");
        assertTrue(fileTracker.trackFile(project, Path.of("dir2/file10")));
        File file10 = createFile("dir2/file10");

        createFile(".gitignore",
                "/file6\n"
                + "/dir1/file8\n"
                + "!/file5\n"
                + "!/dir1/file7\n");
        createFile("dir2/.gitignore",
                "/file10\n"
                + "!/file9\n");

        printExcludeFile(project);

        assertTrackedFiles(project, Optional.of(Path.of("dir1")), Optional.empty(), file8);
        assertTrackedFiles(project, Optional.empty(), Optional.of(0L), file6);
        assertTrackedFiles(project, Optional.empty(), Optional.of(1L), file8, file6, file10);
        assertTrackedFiles(project, Optional.empty(), Optional.empty(), file8, file6, file10);

        fileTracker.unlinkUntrackFiles(project, fileTracker.getTrackedFiles(project, Optional.of(Path.of("dir1")), Optional.empty()));
        assertTrue(file1.exists());
        assertTrue(file2.exists());
        assertTrue(file3.exists());
        assertTrue(file4.exists());
        assertTrue(file5.exists());
        assertTrue(file6.exists());
        assertTrue(file7.exists());
        assertFalse(file8.exists());
        assertTrue(file9.exists());
        assertTrue(file10.exists());

        fileTracker.unlinkUntrackFiles(project, fileTracker.getTrackedFiles(project, Optional.empty(), Optional.of(0L)));
        assertTrue(file1.exists());
        assertTrue(file2.exists());
        assertTrue(file3.exists());
        assertTrue(file4.exists());
        assertTrue(file5.exists());
        assertFalse(file6.exists());
        assertTrue(file7.exists());
        assertFalse(file8.exists());
        assertTrue(file9.exists());
        assertTrue(file10.exists());

        fileTracker.unlinkUntrackFiles(project, fileTracker.getTrackedFiles(project, Optional.empty(), Optional.empty()));
        assertTrue(file1.exists());
        assertTrue(file2.exists());
        assertTrue(file3.exists());
        assertTrue(file4.exists());
        assertTrue(file5.exists());
        assertFalse(file6.exists());
        assertTrue(file7.exists());
        assertFalse(file8.exists());
        assertTrue(file9.exists());
        assertFalse(file10.exists());
    }

    @Test
    void testGitInParentDirectory() throws Exception {
        Git.init().setDirectory(workingDir.toFile()).call();
        Path subProjectDir = workingDir.resolve("subdir");
        subProjectDir.toFile().mkdir();
        Project project = new Project(subProjectDir, Git.open(workingDir.toFile()), SampleProject.EMPTY.getDefinitionForName("test"), Optional.empty());

        File file1 = createFile("subdir/file1");

        assertTrue(fileTracker.trackFile(project, Path.of("file2")));
        File file2 = createFile("subdir/file2");

        printExcludeFile(project);

        assertTrackedFiles(project, Optional.empty(), Optional.empty(), file2);
    }

    private void assertTrackedFiles(Project project, Optional<Path> subPath, Optional<Long> maxDepthOpt, File... expectedFiles) {
        assertEquals(
                Arrays.stream(expectedFiles)
                        .map(File::toPath)
                        .map(p -> project.getPath().relativize(p))
                        .collect(ImmutableSet.toImmutableSet()),
                fileTracker.getTrackedFiles(project, subPath, maxDepthOpt));
    }

    private File createFile(String pathStr) throws Exception {
        return createFile(pathStr, "test");
    }

    private File createFile(String pathStr, String content) throws Exception {
        File file = workingDir.resolve(pathStr).toFile();
        file.getParentFile().mkdirs();
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8, false);
        return file;
    }

    @SneakyThrows
    private void printExcludeFile(Project project) {
        log.info("{}:\n{}", GitExcludeFileTracker.GIT_EXCLUDE_FILE, Files.readString(project
                .getGit()
                .getRepository()
                .getDirectory()
                .toPath()
                .resolve(GitExcludeFileTracker.GIT_EXCLUDE_FILE), StandardCharsets.UTF_8));
    }
}
