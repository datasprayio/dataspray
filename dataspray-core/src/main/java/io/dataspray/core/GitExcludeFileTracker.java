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
import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Optional;
import java.util.Scanner;

/**
 * Tracking of files via ".git/info/exclude" allowing for overrides via ".gitignore".
 * <p>
 * Do not confuse terminology of tracked/untracked with respect of this Tracker and Git's tracked/untracked files.
 * A File tracked by this Tracker is marked as git ignored and is untracked with respect to Git. A user can override
 * by negating ignoring of a file making the file untracked with respect to this Tracker, but tracked with respect to
 * Git.
 */
@Slf4j
@ApplicationScoped
public class GitExcludeFileTracker implements FileTracker {
    public static final String NEXT_LINE_HEADER = "# DO NOT EDIT this and next line; managed by DataSpray";
    /** Path to exclude file within Git Repository's .git metadata folder */
    public static final String GIT_EXCLUDE_FILE = "info/exclude";
    public static final String GIT_EXCLUDE_TMP_FILE = ".git/info/exclude.tmp";

    @Override
    public void unlinkUntrackFiles(Project project, Collection<Path> paths) {
        for (Path path : paths) {
            log.info("Deleting file {}", path);
            makeAbsoluteFromGitWorkTree(project, path).toFile().delete();
        }
        untrackFiles(project, paths);
    }

    @Override
    @SneakyThrows
    public boolean trackFile(Project project, Path relativePath) {

        // Check if user has put an override in to not track this file
        relativePath = makeRelativeToGitWorkTree(project, relativePath);
        GitIgnoreParser gitIgnoreParser = GitIgnoreParser.get(project);
        Optional<Boolean> fileIgnoredWithDotGitignoreOpt = gitIgnoreParser.isFileIgnoredWithDotGitignore(relativePath);
        if (fileIgnoredWithDotGitignoreOpt.isPresent() && !fileIgnoredWithDotGitignoreOpt.get()) {
            return false;
        }

        // Add as tracked if needed by including in .git/info/exclude
        Optional<Boolean> fileIgnoredWithInfoExcludeOpt = gitIgnoreParser.isFileIgnoredWithInfoExclude(relativePath);
        if (fileIgnoredWithInfoExcludeOpt.isEmpty() || !fileIgnoredWithInfoExcludeOpt.get()) {
            Files.writeString(
                    getOrCreateExcludeFile(project).toPath(),
                    System.lineSeparator() + NEXT_LINE_HEADER + System.lineSeparator() + addPrefixSeparator(relativePath.toString()),
                    StandardOpenOption.APPEND);
        }

        return true;
    }

    @Override
    @SneakyThrows
    public ImmutableSet<Path> getTrackedFiles(Project project, Optional<Path> subPathOpt, Optional<Long> maxDepthOpt) {
        Optional<Path> relativeSubPathOpt = subPathOpt.map(subPath -> makeRelativeToGitWorkTree(project, subPath));
        ImmutableSet.Builder<Path> trackedFilesBuilder = ImmutableSet.builder();
        GitIgnoreParser gitignore = GitIgnoreParser.get(project);
        maxDepthOpt = maxDepthOpt.map(maxDepth -> maxDepth + relativeSubPathOpt.map(Path::getNameCount).orElse(0));
        try (Scanner sc = new Scanner(getOrCreateExcludeFile(project))) {
            boolean nextLineIsManaged = false;
            while (sc.hasNextLine()) {
                String nextLine = sc.nextLine().trim();
                if (!nextLineIsManaged) {
                    if (NEXT_LINE_HEADER.equals(nextLine)) {
                        nextLineIsManaged = true;
                    }
                } else {
                    nextLineIsManaged = false;
                    Path trackedRelativePath = Path.of(removePrefixSeparator(nextLine));
                    if (relativeSubPathOpt.isPresent() && !trackedRelativePath.startsWith(relativeSubPathOpt.get())) {
                        continue;
                    }
                    if (maxDepthOpt.isPresent() && (trackedRelativePath.getNameCount() - 1) > maxDepthOpt.get()) {
                        continue;
                    }
                    if (!gitignore.isFileIgnored(trackedRelativePath).orElse(true)) {
                        continue;
                    }
                    trackedFilesBuilder.add(trackedRelativePath);
                }
            }
        }
        return trackedFilesBuilder.build();
    }

    @SneakyThrows
    private void untrackFiles(Project project, Collection<Path> trackedPathsToUntrack) {
        if (trackedPathsToUntrack.isEmpty()) {
            return;
        }
        File excludeFile = getOrCreateExcludeFile(project);
        File excludeTmpFile = getExcludeTmpFile(project);
        ImmutableSet<String> trackedPathStrsToUntrack = trackedPathsToUntrack.stream()
                .map(Path::toString)
                .map(this::addPrefixSeparator)
                .collect(ImmutableSet.toImmutableSet());
        try (Scanner sc = new Scanner(excludeFile);
             FileWriter excludeTmpFileWriter = new FileWriter(excludeTmpFile)) {
            boolean nextLineIsManaged = false;
            while (sc.hasNextLine()) {
                String nextLine = sc.nextLine().trim();
                if (!nextLineIsManaged) {
                    if (NEXT_LINE_HEADER.equals(nextLine)) {
                        nextLineIsManaged = true;
                    } else {
                        excludeTmpFileWriter.write(nextLine + System.lineSeparator());
                    }
                } else {
                    nextLineIsManaged = false;
                    if (!trackedPathStrsToUntrack.contains(nextLine)) {
                        excludeTmpFileWriter.write(System.lineSeparator() + NEXT_LINE_HEADER + System.lineSeparator() + nextLine);
                    }
                }
            }
        }
        Files.move(excludeTmpFile.toPath(), excludeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private String addPrefixSeparator(String pathStr) {
        return pathStr.charAt(0) == File.separatorChar
                ? pathStr
                : File.separator + pathStr;
    }

    private String removePrefixSeparator(String pathStr) {
        return pathStr.charAt(0) == File.separatorChar
                ? pathStr.substring(1)
                : pathStr;
    }

    private File getExcludeTmpFile(Project project) {
        File tmpFile = project.getPath().resolve(GIT_EXCLUDE_TMP_FILE).toFile();
        tmpFile.getParentFile().mkdirs();
        tmpFile.delete();
        return tmpFile;
    }

    @SneakyThrows
    private File getOrCreateExcludeFile(Project project) {
        File excludeFile = project.getGit()
                .getRepository()
                .getDirectory()
                .toPath()
                .resolve(GIT_EXCLUDE_FILE)
                .toFile();
        if (!excludeFile.isFile()) {
            log.debug("Initializing git exclude file {}", excludeFile);
            excludeFile.getParentFile().mkdirs();
            excludeFile.createNewFile();
        }
        return excludeFile;
    }

    private Path makeRelativeToGitWorkTree(Project project, Path path) {
        if (path.isAbsolute()) {
            return getRelativePathFromGitWorkingTreeToProject(project)
                    .resolve(project
                            .getPath()
                            .relativize(path));
        }
        return getRelativePathFromGitWorkingTreeToProject(project)
                .resolve(path);
    }

    private Path makeAbsoluteFromGitWorkTree(Project project, Path path) {
        if (!path.isAbsolute()) {
            return getGitWorkingPath(project)
                    .resolve(path);
        }
        return path;
    }

    @SneakyThrows
    private Path getRelativePathFromGitWorkingTreeToProject(Project project) {
        return getGitWorkingPath(project)
                .toRealPath()
                .relativize(project
                        .getPath()
                        .toRealPath());
    }

    /**
     * Get git working path.
     * <p>
     * Note that JGit internally resolves symlinks, so this method may have a different absolute path from
     * {@link Project#getPath()}. Use {@link Path#toRealPath} to relativize the other path. Keep in mind this method can
     * only be called on paths that actually point to a real file or directory.
     */
    private Path getGitWorkingPath(Project project) {
        return project.getGit().getRepository().getWorkTree().toPath();
    }
}
