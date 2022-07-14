package com.smotana.dataspray.core;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Scanner;

/**
 * Tracking of files via ".git/info/exclude" allowing for overrides via ".gitignore".
 *
 * Do not confuse terminology of tracked/untracked with respect of this Tracker and Git's tracked/untracked files.
 * A File tracked by this Tracker is marked as git ignored and is untracked with respect to Git. A user can override
 * by negating ignoring of a file making the file untracked with respect to this Tracker, but tracked with respect to
 * Git.
 */
@Slf4j
public class GitExcludeFileTracker implements FileTracker {
    public static final String NEXT_LINE_HEADER = "# DO NOT EDIT this and next line; managed by DataSpray".trim();
    public static final String GIT_EXCLUDE_FILE = ".git/info/exclude";
    public static final String GIT_EXCLUDE_TMP_FILE = ".git/info/exclude.tmp";

    @Override
    public void unlinkTrackedFiles(Project project, Optional<Path> subPathOpt, Optional<Long> maxDepthOpt) {
        log.info("Deleting generated files, subpath {} maxDepth {}", subPathOpt, maxDepthOpt);
        subPathOpt = subPathOpt.map(subPath -> makeRelativeToProject(project, subPath));
        ImmutableSet<Path> trackedPathsToUnlink = getTrackedFiles(project, subPathOpt, maxDepthOpt);
        for (Path trackedPath : trackedPathsToUnlink) {
            log.info("Deleting file {}", trackedPath);
            project.getPath().resolve(trackedPath).toFile().delete();
        }
        untrackFiles(project, trackedPathsToUnlink);
    }

    @Override
    @SneakyThrows
    public boolean trackFile(Project project, Path relativePath) {

        // Check if user has put an override in to not track this file
        relativePath = makeRelativeToProject(project, relativePath);
        GitIgnoreParser gitIgnoreParser = GitIgnoreParser.get(project);
        Optional<Boolean> fileIgnoredWithDotGitignoreOpt = gitIgnoreParser.isFileIgnoredWithDotGitignore(relativePath);
        if (fileIgnoredWithDotGitignoreOpt.isPresent() && !fileIgnoredWithDotGitignoreOpt.get()) {
            return false;
        }

        // Add as tracked if needed by includnig in .git/info/exclude
        Optional<Boolean> fileIgnoredWithInfoExcludeOpt = gitIgnoreParser.isFileIgnoredWithInfoExclude(relativePath);
        if (fileIgnoredWithInfoExcludeOpt.isEmpty() || !fileIgnoredWithInfoExcludeOpt.get()) {
            Files.writeString(
                    getOrCreateExcludeFile(project).toPath(),
                    System.lineSeparator() + NEXT_LINE_HEADER + System.lineSeparator() + addPrefixSeparator(relativePath.toString()),
                    StandardOpenOption.APPEND);
        }

        return true;
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

    @SneakyThrows
    private ImmutableSet<Path> getTrackedFiles(Project project, Optional<Path> subPathOpt, Optional<Long> maxDepthOpt) {
        ImmutableSet.Builder<Path> trackedFilesBuilder = ImmutableSet.builder();
        GitIgnoreParser gitignore = GitIgnoreParser.get(project);
        maxDepthOpt = maxDepthOpt.map(maxDepth -> maxDepth + subPathOpt.map(Path::getNameCount).orElse(0));
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
                    if (subPathOpt.isPresent() && !trackedRelativePath.startsWith(subPathOpt.get())) {
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
    private void untrackFiles(Project project, ImmutableSet<Path> trackedPathsToUntrack) {
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

    private File getExcludeTmpFile(Project project) {
        File tmpFile = project.getPath().resolve(GIT_EXCLUDE_TMP_FILE).toFile();
        tmpFile.getParentFile().mkdirs();
        tmpFile.delete();
        return tmpFile;
    }

    @SneakyThrows
    private File getOrCreateExcludeFile(Project project) {
        File excludeFile = project.getPath().resolve(GIT_EXCLUDE_FILE).toFile();
        if (!excludeFile.isFile()) {
            log.info("Initializing git exclude file {}", excludeFile);
            excludeFile.getParentFile().mkdirs();
            excludeFile.createNewFile();
        }
        return excludeFile;
    }

    private Path makeRelativeToProject(Project project, Path relativeFilePath) {
        if (relativeFilePath.isAbsolute()) {
            return project.getPath().relativize(relativeFilePath);
        }
        return relativeFilePath;
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(FileTracker.class).to(GitExcludeFileTracker.class).asEagerSingleton();
            }
        };
    }
}
