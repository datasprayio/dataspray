package com.smotana.dataspray.core;

import java.nio.file.Path;
import java.util.Optional;

public interface FileTracker {

    void unlinkTrackedFiles(Project project, Optional<Path> subPath, Optional<Long> maxDepthOpt);

    /**
     * @return True if file is marked as tracked successfully and unlinked;
     * False if file may or may not be present but definitely cannot be touched
     */
    boolean trackFile(Project project, Path path);
}
