package io.dataspray.core;

import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskVersions;

import java.io.File;

public interface Runtime {

    void statusAll(String apiKey, Project project);

    void status(String dataSprayApiKey, Project project, String processorName);

    void deploy(String apiKey, Project project, String processorName, boolean activateVersion);

    String upload(String apiKey, Project project, String processorName, File codeZipFile);

    String publish(String apiKey, Project project, String processorName, String codeUrl, boolean activateVersion);

    TaskStatus activateVersion(String apiKey, Project project, String processorName, String version);

    TaskStatus pause(String apiKey, Project project, String processorName);

    TaskStatus resume(String apiKey, Project project, String processorName);

    TaskVersions listVersions(String apiKey, Project project, String processorName);

    TaskStatus delete(String apiKey, Project project, String processorName);
}
