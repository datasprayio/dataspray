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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public interface FileTracker {

    /**
     * Get all tracked files.
     *
     * @param subPath Optional sub-path to filter by
     * @param maxDepthOpt Optional maximum depth of sub-path
     * @return Absolute paths to tracked files
     */
    ImmutableSet<Path> getTrackedFiles(Project project, Optional<Path> subPath, Optional<Long> maxDepthOpt);

    /**
     * Deletes files from disk and untracks them.
     *
     * @param relativeToProjectOrAbsolutePaths Either absolute paths or relative to project root
     */
    void unlinkUntrackFiles(Project project, Collection<Path> relativeToProjectOrAbsolutePaths);

    /**
     * Track specific file. Not required to exist.
     *
     * @param relativeToProjectOrAbsolutePath Either absolute path or relative to project root
     * @return True if file is marked as tracked successfully and unlinked;
     * False if file may or may not be present but definitely cannot be touched
     */
    boolean trackFile(Project project, Path relativeToProjectOrAbsolutePath);
}
