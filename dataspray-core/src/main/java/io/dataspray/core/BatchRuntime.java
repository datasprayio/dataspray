package io.dataspray.core;

import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskVersions;

import java.io.File;

public interface BatchRuntime {

    void setCatalog(String dataSprayApiKey, Project project, String cat);
}
