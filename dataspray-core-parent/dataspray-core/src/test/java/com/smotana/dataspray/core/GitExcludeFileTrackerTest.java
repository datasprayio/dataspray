package com.smotana.dataspray.core;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.smotana.dataspray.core.sample.SampleProject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static com.smotana.dataspray.core.GitExcludeFileTracker.GIT_EXCLUDE_FILE;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class GitExcludeFileTrackerTest extends CoreAbstractTest {

    @TempDir
    private File workingDir;
    @Inject
    private FileTracker fileTracker;

    @Override
    protected void configure() {
        super.configure();

        install(GitExcludeFileTracker.module());
    }

    @Test
    void test() throws Exception {
        Git.init().setDirectory(workingDir).call();
        Project project = new Project(workingDir.toPath(), Git.open(workingDir), SampleProject.EMPTY.getDefinitionForName("test"));

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

        createFile(".gitignore", """
                /file6
                /dir1/file8
                !/file5
                !/dir1/file7
                """);
        createFile("dir2/.gitignore", """
                /file10
                !/file9
                """);

        log.info("{}:\n{}", GIT_EXCLUDE_FILE, Files.readString(project.getPath().resolve(GIT_EXCLUDE_FILE), StandardCharsets.UTF_8));

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
        File file = workingDir.toPath().resolve(pathStr).toFile();
        file.getParentFile().mkdirs();
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8, false);
        return file;
    }
}