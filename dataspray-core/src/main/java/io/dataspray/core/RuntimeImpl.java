package io.dataspray.core;

import com.google.common.base.Strings;
import io.dataspray.core.definition.model.Item;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import io.dataspray.core.definition.model.StreamLink;
import io.dataspray.stream.client.StreamApi;
import io.dataspray.stream.control.client.model.DeployRequest;
import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskVersion;
import io.dataspray.stream.control.client.model.TaskVersions;
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
import java.util.Optional;
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
    @SneakyThrows
    public void status(String apiKey, Project project, String processorName) {
        if (project.getDefinition().getJavaProcessors().stream()
                .map(Item::getName)
                .noneMatch(processorName::equals)) {
            throw new RuntimeException("Task not found: " + processorName);
        }
        TaskStatus status = streamApi.control(apiKey).status(processorName);
        printStatus(status);
    }

    @Override
    public void deploy(String apiKey, Project project, String processorName, boolean activateVersion) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        // Find code file
        final File codeZipFile;
        if (processor instanceof JavaProcessor) {
            codeZipFile = CodegenImpl.getProcessorDir(project, processor.getTaskId())
                    .resolve(Path.of("target", processor.getTaskId() + ".jar")).toFile();
        } else {
            throw new RuntimeException("Cannot determine file to upload for task type " + processor.getClass().getSimpleName());
        }

        // Deploy
        String codeUrl = upload(apiKey, project, processorName, codeZipFile);
        String publishedVersion = publish(apiKey, project, processorName, codeUrl, activateVersion);
    }

    @Override
    @SneakyThrows
    public String upload(String apiKey, Project project, String processorName, File codeZipFile) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());
        checkState(codeZipFile.isFile(), "Missing code zip file, forgot to install? Expecting: %s", codeZipFile.getPath());

        // First get S3 upload presigned url
        log.info("Requesting permission to upload {}", codeZipFile);
        UploadCodeResponse uploadCodeResponse = streamApi.control(apiKey).uploadCode(new UploadCodeRequest()
                .taskId(processor.getTaskId())
                .contentLengthBytes(codeZipFile.length()));

        // Upload to S3
        log.info("Uploading to {}", uploadCodeResponse.getPresignedUrl());
        streamApi.uploadCode(uploadCodeResponse.getPresignedUrl(), codeZipFile);

        log.info("File available at {}", uploadCodeResponse.getCodeUrl());

        return uploadCodeResponse.getCodeUrl();
    }

    @Override
    @SneakyThrows
    public String publish(String apiKey, Project project, String processorName, String codeUrl, boolean activateVersion) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());
        String handler = Optional.ofNullable(Strings.emptyToNull(processor.getHandler()))
                .orElseGet(() -> project.getDefinition().getJavaPackage() + ".Runner");

        // Publish version
        log.info("Publishing task {} with inputs {} outputs {} handler {}",
                processor.getName(),
                processor.getInputStreams().stream().map(StreamLink::getStreamName).collect(Collectors.toSet()),
                processor.getOutputStreams().stream().map(StreamLink::getStreamName).collect(Collectors.toSet()),
                handler);
        TaskVersion deployedVersion = streamApi.control(apiKey).deployVersion(processor.getTaskId(), new DeployRequest()
                .runtime(DeployRequest.RuntimeEnum.JAVA11)
                .handler(handler)
                .inputQueueNames(processor.getInputStreams().stream()
                        .map(StreamLink::getStreamName)
                        .collect(Collectors.toList()))
                .codeUrl(codeUrl)
                .switchToNow(activateVersion));

        if (activateVersion) {
            log.info("Task {} published and activated as version {}: {}",
                    deployedVersion.getTaskId(), deployedVersion.getVersion(), deployedVersion.getDescription());
        } else {
            log.info("Task {} published as version {}: {}",
                    deployedVersion.getTaskId(), deployedVersion.getVersion(), deployedVersion.getDescription());
        }
        return deployedVersion.getVersion();
    }

    @Override
    @SneakyThrows
    public TaskStatus activateVersion(String apiKey, Project project, String processorName, String version) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        // Switch to this version
        log.info("Activating version {} for task {}", version, processorName);
        TaskStatus taskStatus = streamApi.control(apiKey).activateVersion(processor.getTaskId(), version);
        log.info("Version active!");

        return taskStatus;
    }

    @Override
    @SneakyThrows
    public TaskStatus pause(String apiKey, Project project, String processorName) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        log.info("Pausing {}", processor.getTaskId());
        TaskStatus taskStatus = streamApi.control(apiKey).pause(processor.getTaskId());
        log.info("Task set to be paused");
        printStatus(taskStatus);

        return taskStatus;
    }

    @Override
    @SneakyThrows
    public TaskStatus resume(String apiKey, Project project, String processorName) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        log.info("Resuming {}", processor.getTaskId());
        TaskStatus taskStatus = streamApi.control(apiKey).resume(processor.getTaskId());
        log.info("Task set to be resumed");
        printStatus(taskStatus);

        return taskStatus;
    }

    @Override
    @SneakyThrows
    public TaskVersions listVersions(String apiKey, Project project, String processorName) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        TaskVersions versions = streamApi.control(apiKey).getVersions(processor.getTaskId());
        printVersions(versions);

        return versions;
    }

    @Override
    @SneakyThrows
    public TaskStatus delete(String apiKey, Project project, String processorName) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        TaskStatus status = streamApi.control(apiKey).delete(processor.getTaskId());

        printStatus(status);

        return status;
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

    private void printVersions(TaskVersions versions) {
        String activeVersion = versions.getStatus().getVersion();
        log.info("{}\t{}\t{}", "Status", "Version", "Description");
        log.info("---\t---\t---");
        versions.getVersions().stream()
                .map(version -> String.format("%s\t%s\t %s",
                        !version.getVersion().equals(activeVersion) ? ""
                                : versions.getStatus().getStatus().name(),
                        version.getVersion(),
                        version.getDescription()))
                .forEach(content -> log.info("{}", content));
    }

    private String getStatusEnumAsChar(TaskStatus.StatusEnum statusEnum) {
        switch (statusEnum) {
            case RUNNING:
                return "✔";
            case PAUSED:
                return "✘";
            case NOTFOUND:
                return "!";
            default:
                return "?";
        }
    }
}
