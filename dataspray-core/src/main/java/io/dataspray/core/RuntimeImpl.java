package io.dataspray.core;

import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.StreamLink;
import io.dataspray.stream.client.StreamApi;
import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.control.client.model.DeployRequest;
import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskVersion;
import io.dataspray.stream.control.client.model.UploadCodeRequest;
import io.dataspray.stream.control.client.model.UploadCodeResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@ApplicationScoped
public class RuntimeImpl implements Runtime {

    @Inject
    ContextBuilder contextBuilder;
    @Inject
    StreamApi streamApi;

    @Override
    @SneakyThrows
    public void statusAll(String apiKey, Project project) {
        streamApi.control(apiKey).statusAll()
                .getTasks()
                .forEach(this::printStatus);
    }

    @Override
    public void deploy(String apiKey, Project project, JavaProcessor processor) {
        switch (processor.getTarget()) {
            case DATASPRAY:
                deployStream(apiKey, project, processor);
                break;
            case SAMZA:
            case FLINK:
                throw new RuntimeException("Not yet implemented");
            default:
                throw new RuntimeException("Unknown target " + processor.getTarget());
        }
    }

    @SneakyThrows
    private void deployStream(String apiKey, Project project, JavaProcessor processor) {
        // First get S3 upload presigned url
        String taskId = processor.getNameDir();
        Path processorDir = CodegenImpl.getProcessorDir(project, taskId);
        File codeZipFile = processorDir.resolve(Path.of("target", taskId + ".jar")).toFile();
        log.info("Preparing upload of {}", codeZipFile);
        checkState(codeZipFile.isFile(), "Missing code zip file, forgot to install? Expecting: %s", codeZipFile.getPath());
        ControlApi controlApi = streamApi.control(apiKey);
        UploadCodeResponse uploadCodeResponse = controlApi.uploadCode(new UploadCodeRequest()
                .taskId(taskId)
                .contentLengthBytes(codeZipFile.length()));

        // Upload to S3
        log.info("Uploading to {}", uploadCodeResponse.getCodeUrl());
        streamApi.uploadCode(uploadCodeResponse.getPresignedUrl(), codeZipFile);

        // Publish version
        log.info("Publishing new version");
        TaskVersion deployedVersion = controlApi.deployVersion(taskId, new DeployRequest()
                .runtime(DeployRequest.RuntimeEnum.JAVA11)
                .inputQueueNames(processor.getInputStreams().stream()
                        .map(StreamLink::getStreamName)
                        .collect(Collectors.toList()))
                .codeUrl(uploadCodeResponse.getCodeUrl()));

        // Switch to this version
        log.info("Switching code to new version {}", deployedVersion.getVersion());
        TaskStatus taskStatus = controlApi.activateVersion(taskId, deployedVersion.getVersion());

        log.info("Deployed task {}", taskStatus.getTaskId());
        printStatus(taskStatus);
    }

    private String getCommitHash(Project project) throws GitAPIException {
        Iterator<RevCommit> revs = project.getGit()
                .log()
                .setMaxCount(1)
                .call()
                .iterator();
        if (revs.hasNext()) {
            return revs.next().getName();
        } else {
            return "unknown";
        }
    }

    private void printStatus(TaskStatus taskStatus) {
        log.info("{}\t{}", taskStatus.getTaskId(), taskStatus.getStatus());
    }
}
