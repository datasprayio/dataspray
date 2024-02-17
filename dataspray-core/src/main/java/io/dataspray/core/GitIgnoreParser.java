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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public class GitIgnoreParser {

    private final Project project;
    private final Cache<String, IgnoreNode> nodeCache = CacheBuilder.newBuilder()
            .weakValues()
            .build();

    private GitIgnoreParser(Project project) {
        this.project = project;
    }

    public static GitIgnoreParser get(Project project) {
        return new GitIgnoreParser(project);
    }

    @SneakyThrows
    public Optional<Boolean> isFileIgnored(Path path) {
        Path relativePath = makeRelativeToProject(path);
        return isFileIgnoredWithDotGitignore(relativePath)
                .or(() -> isFileIgnoredWithInfoExclude(relativePath));
    }

    /**
     * Re-implemented internals of JGit to load each .gitignore and exclude file separately. This is because JGit 6.2
     * has a bug where the order of files is incorrect causing the exclude file to take precedence over user defined
     * local .gitignore file. Once JGit 6.3 is released, this can be simplified; an example implementation can be seen
     * in CGitIgnoreTest.jgitIgnoredAndUntracked method.
     *
     * Bug report: https://bugs.eclipse.org/bugs/show_bug.cgi?id=580381
     * Simpler impl once bug is fixed:
     * https://git.eclipse.org/c/jgit/jgit.git/tree/org.eclipse.jgit.test/tst/org/eclipse/jgit/ignore/CGitIgnoreTest.java?h=stable-6.2#n106
     */
    @SneakyThrows
    public Optional<Boolean> isFileIgnoredWithDotGitignore(Path path) {
        Path relativeFilePath = makeRelativeToProject(path);
        // Iterate over directory hierarchy checking each .gitignore file
        Optional<Path> currPathOpt = Optional.of(relativeFilePath);
        Optional<Boolean> isIgnoredOpt;
        Repository repo = project.getGit().getRepository();
        FS fs = repo.getFS();
        do {
            currPathOpt = Optional.ofNullable(currPathOpt.get().getParent());
            Path currGitIgnorePath = project.getPath().resolve(
                    currPathOpt.map(p -> p + File.separator).orElse("") + Constants.GITIGNORE_FILENAME);
            Path filePathRelativeToGitignore = currPathOpt.map(currPath -> currPath.relativize(relativeFilePath)).orElse(relativeFilePath);
            isIgnoredOpt = isFileIgnored(filePathRelativeToGitignore, currGitIgnorePath);
            if (isIgnoredOpt.isPresent()) {
                return isIgnoredOpt;
            }
        } while (currPathOpt.isPresent());

        // Custom info exclude
        Path customExcludesPath = repo.getConfig().getPath(
                ConfigConstants.CONFIG_CORE_SECTION, null,
                ConfigConstants.CONFIG_KEY_EXCLUDESFILE, fs, null, null);
        if (customExcludesPath != null) {
            isIgnoredOpt = isFileIgnored(relativeFilePath, customExcludesPath);
            if (isIgnoredOpt.isPresent()) {
                return isIgnoredOpt;
            }
        }

        return isIgnoredOpt;
    }

    @SneakyThrows
    public Optional<Boolean> isFileIgnoredWithInfoExclude(Path path) {
        Path relativeFilePath = makeRelativeToProject(path);
        return isFileIgnored(relativeFilePath, project.getPath().resolve(
                Constants.DOT_GIT + "/" + Constants.INFO_EXCLUDE));
    }

    @SneakyThrows
    private Optional<Boolean> isFileIgnored(Path filePath, Path gitIgnorePath) {
        IgnoreNode node = nodeCache.getIfPresent(gitIgnorePath.toString());
        if (node == null) {
            node = new IgnoreNode();
            try (FileInputStream fis = new FileInputStream(gitIgnorePath.toFile())) {
                node.parse(gitIgnorePath.toString(), fis);
            } catch (FileNotFoundException ex) {
                return Optional.empty();
            }
            nodeCache.put(gitIgnorePath.toString(), node);
        }
        return Optional.ofNullable(node.checkIgnored(filePath.toString(), false));
    }

    private Path makeRelativeToProject(Path relativeFilePath) {
        if (relativeFilePath.isAbsolute()) {
            return project.getPath().relativize(relativeFilePath);
        }
        return relativeFilePath;
    }
}
