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

import io.dataspray.client.DataSprayClient;
import io.dataspray.core.definition.model.DynamoState;
import io.dataspray.core.definition.model.Item;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import io.dataspray.core.definition.model.StreamLink;
import io.dataspray.core.definition.model.TypescriptProcessor;
import io.dataspray.stream.control.client.ApiException;
import io.dataspray.stream.control.client.model.DeployRequest;
import io.dataspray.stream.control.client.model.DeployRequest.RuntimeEnum;
import io.dataspray.stream.control.client.model.DeployRequestDynamoState;
import io.dataspray.stream.control.client.model.DeployRequestEndpoint;
import io.dataspray.stream.control.client.model.DeployRequestEndpointCors;
import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskVersion;
import io.dataspray.stream.control.client.model.TaskVersions;
import io.dataspray.stream.control.client.model.UploadCodeRequest;
import io.dataspray.stream.control.client.model.UploadCodeResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.util.Iterator;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@ApplicationScoped
public class StreamRuntimeImpl implements StreamRuntime {

    @Inject
    ContextBuilder contextBuilder;
    @Inject
    Builder builder;

    @Override
    public void ping(Organization organization) throws ApiException {
        DataSprayClient.get(organization.toAccess())
                .health()
                .ping();
    }

    @Override
    @SneakyThrows
    public void statusAll(Organization organization, Project project) {
        DataSprayClient.get(organization.toAccess())
                .control()
                // TODO add pagination
                .statusAll(organization.getName(), null)
                .getTasks()
                .forEach(this::printStatus);
    }

    @Override
    @SneakyThrows
    public void status(Organization organization, Project project, String processorName) {
        if (project.getDefinition().getProcessors().stream()
                .map(Item::getName)
                .noneMatch(processorName::equals)) {
            throw new RuntimeException("Task not found: " + processorName);
        }
        TaskStatus status = DataSprayClient.get(organization.toAccess())
                .control()
                .status(organization.getName(), processorName);
        printStatus(status);
    }

    @Override
    public void deploy(Organization organization, Project project, String processorName, boolean activateVersion) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        // Find code file
        final File codeZipFile = builder.getBuiltArtifact(project, processor.getName())
                // If not found, try building it
                .orElseGet(() -> {
                    log.info("Artifact not found for {}, attempting to build it first", processor.getName());
                    return builder.build(project, processor.getName());
                })
                .getCodeZipFile();

        // Deploy
        String codeUrl = upload(organization, project, processorName, codeZipFile);
        String publishedVersion = publish(organization, project, processorName, codeUrl, activateVersion);
    }

    @Override
    @SneakyThrows
    public String upload(Organization organization, Project project, String processorName, File codeZipFile) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());
        checkState(codeZipFile.isFile(), "Missing code zip file, forgot to install? Expecting: %s", codeZipFile.getPath());

        // First get S3 upload presigned url
        log.info("Requesting permission to upload {}", codeZipFile);
        UploadCodeResponse uploadCodeResponse = DataSprayClient.get(organization.toAccess())
                .control()
                .uploadCode(organization.getName(), new UploadCodeRequest()
                        .taskId(processor.getProcessorId())
                        .contentLengthBytes(codeZipFile.length()));

        // Upload to S3
        log.info("Uploading to {}", uploadCodeResponse.getPresignedUrl());
        DataSprayClient.get(organization.toAccess())
                .uploadCode(uploadCodeResponse.getPresignedUrl(), codeZipFile);

        log.info("File available at {}", uploadCodeResponse.getCodeUrl());

        return uploadCodeResponse.getCodeUrl();
    }

    @Override
    @SneakyThrows
    public String publish(Organization organization, Project project, String processorName, String codeUrl, boolean activateVersion) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        String handler;
        // TODO Eventually this needs to be inferred from definition or project .nvmrc/.sdkman files
        RuntimeEnum runtime;
        if (processor instanceof JavaProcessor) {
            handler = project.getDefinition().getJavaPackage() + ".Runner";
            runtime = RuntimeEnum.JAVA21;
        } else if (processor instanceof TypescriptProcessor) {
            // TODO Double check for TS this is the right handler https://docs.aws.amazon.com/lambda/latest/dg/foundation-progmodel.html
            handler = "index.js";
            runtime = RuntimeEnum.NODEJS20_X;
        } else {
            throw new RuntimeException("Cannot publish processor " + processor.getName() + " of unknown type " + processor.getClass().getCanonicalName());
        }

        // Publish version
        log.info("Publishing task {} with inputs {} outputs {} handler {}",
                processor.getName(),
                processor.getInputStreams().stream().map(StreamLink::getStreamName).collect(Collectors.toSet()),
                processor.getOutputStreams().stream().map(StreamLink::getStreamName).collect(Collectors.toSet()),
                handler);
        TaskVersion deployedVersion = DataSprayClient.get(organization.toAccess())
                .control()
                .deployVersion(organization.getName(), processor.getProcessorId(), new DeployRequest()
                        .runtime(runtime)
                        .handler(handler)
                        .inputQueueNames(processor.getInputStreams().stream()
                                .map(StreamLink::getStreamName)
                                .collect(Collectors.toList()))
                        .codeUrl(codeUrl)
                        .endpoint(processor.getWebOpt()
                                .map(web -> new DeployRequestEndpoint()
                                        .isPublic(web.getIsPublic())
                                        .cors(web.getCorsOpt()
                                                .map(cors -> new DeployRequestEndpointCors()
                                                        .allowOrigins(cors.getAllowOrigins().stream().toList())
                                                        .allowMethods(cors.getAllowMethods().stream().toList())
                                                        .allowHeaders(cors.getAllowHeaders().stream().toList())
                                                        .maxAge(cors.getMaxAge())
                                                        .allowCredentials(cors.getAllowCredentials()))
                                                .orElse(null)))
                                .orElse(null))
                        .dynamoState(!processor.isHasDynamoState() ? null : new DeployRequestDynamoState()
                                .lsiCount(project.getDefinition().getDynamoStateOpt().map(DynamoState::getLsiCount).orElse(0L))
                                .gsiCount(project.getDefinition().getDynamoStateOpt().map(DynamoState::getGsiCount).orElse(0L)))
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
    public TaskStatus activateVersion(Organization organization, Project project, String processorName, String version) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        // Switch to this version
        log.info("Activating version {} for task {}", version, processorName);
        TaskStatus taskStatus = DataSprayClient.get(organization.toAccess())
                .control()
                .activateVersion(organization.getName(), processor.getProcessorId(), version);
        log.info("Version active!");

        return taskStatus;
    }

    @Override
    @SneakyThrows
    public TaskStatus pause(Organization organization, Project project, String processorName) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        log.info("Pausing {}", processor.getProcessorId());
        TaskStatus taskStatus = DataSprayClient.get(organization.toAccess())
                .control()
                .pause(organization.getName(), processor.getProcessorId());
        log.info("Task set to be paused");
        printStatus(taskStatus);

        return taskStatus;
    }

    @Override
    @SneakyThrows
    public TaskStatus resume(Organization organization, Project project, String processorName) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        log.info("Resuming {}", processor.getProcessorId());
        TaskStatus taskStatus = DataSprayClient.get(organization.toAccess())
                .control()
                .resume(organization.getName(), processor.getProcessorId());
        log.info("Task set to be resumed");
        printStatus(taskStatus);

        return taskStatus;
    }

    @Override
    @SneakyThrows
    public TaskVersions listVersions(Organization organization, Project project, String processorName) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        TaskVersions versions = DataSprayClient.get(organization.toAccess())
                .control()
                .getVersions(organization.getName(), processor.getProcessorId());
        printVersions(versions);

        return versions;
    }

    @Override
    @SneakyThrows
    public TaskStatus delete(Organization organization, Project project, String processorName) {
        Processor processor = project.getProcessorByName(processorName);
        checkState(Processor.Target.DATASPRAY.equals(processor.getTarget()),
                "Not yet implemented: %s", processor.getTarget());

        TaskStatus status = DataSprayClient.get(organization.toAccess())
                .control()
                .delete(organization.getName(), processor.getProcessorId());

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
