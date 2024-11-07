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

import com.google.common.base.Preconditions;
import com.jcabi.aspects.Cacheable;
import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.model.Processor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import org.eclipse.jgit.api.Git;

import java.nio.file.Path;
import java.util.Optional;

@Value
public class Project {
    @NonNull
    Path path;
    @NonNull
    Git git;
    @NonNull
    Definition definition;
    /** If set, cwd points to this processor */
    @NonNull
    Optional<String> activeProcessor;

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public Processor getProcessorByName(String name) {
        return getDefinition().getProcessors().stream()
                .filter(p -> p.getName().equals(name))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Processor not found: " + name));
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public Path getProcessorDir(Processor processor) {
        return getPath().resolve(processor.getNameDir());
    }

    public Path makeRelativeToGitWorkTree(Path path) {
        Path relativeToProjectPath = path.isAbsolute()
                ? getPath().relativize(path)
                : path;
        return getRelativePathFromGitWorkTreeToProject()
                .resolve(relativeToProjectPath);
    }

    public Path makeAbsoluteFromGitWorkTree(Path path) {
        return path.isAbsolute()
                ? path
                : subtractPath(getPath(), getRelativePathFromGitWorkTreeToProject()).resolve(path);
    }

    /**
     * Get git working path.
     * <p>
     * Note that JGit internally resolves symlinks, so this method ensures the absolute path is constructed with same
     * common path as project path {@link #getPath} to make sure calls to {@link Path#relativize(Path)} are constructed
     * properly.
     */
    @SneakyThrows
    public Path getGitWorkTreePath() {
        return subtractPath(getPath(), getRelativePathFromGitWorkTreeToProject());
    }

    @SneakyThrows
    private Path getRelativePathFromGitWorkTreeToProject() {
        return getGit()
                .getRepository()
                .getWorkTree()
                .toPath()
                .toRealPath()
                .relativize(getPath()
                        .toRealPath());
    }

    /**
     * Subtract a path. E.g. given absolutePath of /a/b/c and subtractRelativePath of b/c, the result is /a.
     */
    private Path subtractPath(Path absolutePath, Path subtractRelativePath) {
        absolutePath = absolutePath.normalize();
        subtractRelativePath = subtractRelativePath.normalize();

        // Path.of("").nameCount() weirdly returns 1, instead check for empty string here
        if (subtractRelativePath.toString().isEmpty()) {
            return absolutePath;
        }

        Preconditions.checkArgument(absolutePath.endsWith(subtractRelativePath), "Cannot subtract paths, '%s' does not end with '%s'", absolutePath, subtractRelativePath);

        // Subtract the relative path
        int elementsToRemove = subtractRelativePath.getNameCount();
        Path resultPath = absolutePath;
        for (int i = 0; i < elementsToRemove; i++) {
            resultPath = resultPath.getParent();
        }

        // Ensure we return an absolute path by re-attaching the root if necessary
        return resultPath.isAbsolute() ? resultPath : absolutePath.getRoot().resolve(resultPath);
    }
}
