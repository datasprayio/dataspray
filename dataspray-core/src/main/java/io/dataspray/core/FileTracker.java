package io.dataspray.core;

import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public interface FileTracker {

    ImmutableSet<Path> getTrackedFiles(Project project, Optional<Path> subPath, Optional<Long> maxDepthOpt);

    void unlinkUntrackFiles(Project project, Collection<Path> relativePaths);

    /**
     * @return True if file is marked as tracked successfully and unlinked;
     * False if file may or may not be present but definitely cannot be touched
     */
    boolean trackFile(Project project, Path path);
}
