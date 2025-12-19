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

import com.google.common.base.Strings;
import io.dataspray.client.DataSprayClient;
import io.dataspray.core.definition.model.DataFormat.Serde;
import io.dataspray.core.definition.model.DatasprayStore;
import io.dataspray.core.definition.model.DynamoState;
import io.dataspray.core.definition.model.Item;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import io.dataspray.core.definition.model.StreamLink;
import io.dataspray.core.definition.model.TypescriptProcessor;
import io.dataspray.stream.control.client.ApiException;
import io.dataspray.stream.control.client.ApiResponse;
import io.dataspray.stream.control.client.model.DeployRequest;
import io.dataspray.stream.control.client.model.DeployRequest.RuntimeEnum;
import io.dataspray.stream.control.client.model.DeployRequestDynamoState;
import io.dataspray.stream.control.client.model.DeployRequestEndpoint;
import io.dataspray.stream.control.client.model.DeployRequestEndpointCors;
import io.dataspray.stream.control.client.model.SchemaFormat;
import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskStatuses;
import io.dataspray.stream.control.client.model.TaskVersion;
import io.dataspray.stream.control.client.model.TaskVersions;
import io.dataspray.stream.control.client.model.UpdateTopicSchemaRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@ApplicationScoped
public class StreamRuntimeImpl implements StreamRuntime {

    @Inject
    ContextBuilder contextBuilder;
    @Inject
    Builder builder;
    @Inject
    Codegen codegen;

    @Override
    public void ping(Organization organization) throws ApiException {
        DataSprayClient.get(organization.toAccess())
                .health()
                .ping();
    }

    @Override
    @SneakyThrows
    public void statusAll(Organization organization, Project project) {
        boolean printHeader = true;
        Optional<String> cursorOpt = Optional.empty();
        do {
            TaskStatuses taskStatuses = DataSprayClient.get(organization.toAccess())
                    .control()
                    .statusAll(organization.getName(), cursorOpt.orElse(null));
            cursorOpt = Optional.ofNullable(Strings.emptyToNull(taskStatuses.getCursor()));
            for (TaskStatus taskStatus : taskStatuses.getTasks()) {
                printStatus(taskStatus, printHeader);
                printHeader = false;
            }
        } while (cursorOpt.isPresent());
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
        printStatus(status, true);
    }

    @Override
    public TaskVersion deploy(Organization organization, Project project, String processorName, boolean activateVersion) {
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

        String handler;
        // TODO Eventually this needs to be inferred from project definition, maven pom.xml or project .nvmrc/.sdkman files
        RuntimeEnum runtime;
        if (processor instanceof JavaProcessor) {
            handler = project.getDefinition().getJavaPackage() + ".Runner";
            runtime = RuntimeEnum.JAVA21;
        } else if (processor instanceof TypescriptProcessor) {
            // TODO Double check for TS this is the right handler https://docs.aws.amazon.com/lambda/latest/dg/foundation-progmodel.html
            handler = "index.js";
            runtime = RuntimeEnum.NODEJS20_X;
        } else {
            throw new RuntimeException("Cannot publish task " + processor.getName() + " of unknown type " + processor.getClass().getCanonicalName());
        }

        log.info("Task {} found with inputs {} outputs {}{}",
                processor.getName(),
                processor.getInputStreams().stream().map(StreamLink::getStreamName).collect(Collectors.toSet()),
                processor.getOutputStreams().stream().map(StreamLink::getStreamName).collect(Collectors.toSet()),
                processor.getWebOpt().isPresent() ? " and web endpoint" : "");
        DeployRequest deployRequest = new DeployRequest()
                .runtime(runtime)
                .handler(handler)
                .inputQueueNames(processor.getInputStreams().stream()
                        .map(StreamLink::getStreamName)
                        .collect(Collectors.toList()))
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
                .switchToNow(activateVersion);

        TaskVersion deployedVersion = DataSprayClient.get(organization.toAccess())
                .uploadAndPublish(
                        organization.getName(),
                        processor.getProcessorId(),
                        codeZipFile,
                        deployRequest::codeUrl);

        if (activateVersion) {
            log.info("Task {} published and activated as version {}: {}",
                    deployedVersion.getTaskId(), deployedVersion.getVersion(), deployedVersion.getDescription());
        } else {
            log.info("Task {} published as version {}: {}",
                    deployedVersion.getTaskId(), deployedVersion.getVersion(), deployedVersion.getDescription());
        }
        return deployedVersion;
    }

    @Override
    @SneakyThrows
    public void uploadSchema(Organization organization, Project project, StreamLink streamLink) {
        if (!Serde.JSON.equals(streamLink.getDataFormat().getSerde())) {
            log.debug("Not updating schema for store {} stream {} format {} format type {} not supported",
                    streamLink.getStoreName(),
                    streamLink.getStreamName(),
                    streamLink.getDataFormat().getName(),
                    streamLink.getDataFormat().getSerde().name());
            return;
        }
        if (!(streamLink.getStore() instanceof DatasprayStore)) {
            log.debug("Not updating schema for store {} stream {} format {} store type not supported",
                    streamLink.getStoreName(),
                    streamLink.getStreamName(),
                    streamLink.getDataFormat().getName());
            return;
        }
        Path schemaPath = codegen.getDataFormatSchema(project, streamLink.getDataFormat());
        String schema;
        try {
            schema = Files.readString(schemaPath);
        } catch (IOException ex) {
            log.warn("Cannot read schema file for store {} stream {} format {}",
                    streamLink.getStoreName(),
                    streamLink.getStreamName(),
                    streamLink.getDataFormat().getName(),
                    ex);
            return;
        }

        log.info("Starting schema upload for store {} stream {} schema {}",
                streamLink.getStoreName(),
                streamLink.getStreamName(),
                streamLink.getDataFormat().getName());
        ApiResponse<Void> response = DataSprayClient.get(organization.toAccess())
                .control()
                .updateTopicSchemaWithHttpInfo(
                        organization.getName(),
                        streamLink.getStreamName(),
                        new UpdateTopicSchemaRequest()
                                .schema(schema)
                                .format(SchemaFormat.JSON));
        if (response.getStatusCode() == 201) {
            log.info("Schema updated for store {} stream {} schema {}",
                    streamLink.getStoreName(),
                    streamLink.getStreamName(),
                    streamLink.getDataFormat().getName());
        } else if (response.getStatusCode() == 200 || response.getStatusCode() == 204) {
            log.info("Schema uploaded but unchanged or unneeded for store {} stream {} schema {}",
                    streamLink.getStoreName(),
                    streamLink.getStreamName(),
                    streamLink.getDataFormat().getName());
        } else {
            log.info("Unexpected return status {} when uploading schema for store {} stream {} schema {}",
                    response.getStatusCode(),
                    streamLink.getStoreName(),
                    streamLink.getStreamName(),
                    streamLink.getDataFormat().getName());
        }
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
        printStatus(taskStatus, true);

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
        printStatus(taskStatus, true);

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

        printStatus(status, true);

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

    private void printStatus(TaskStatus taskStatus, boolean printHeader) {
        if (printHeader) {
            log.info("{}\t{}\t{}\t{}\t{}",
                    "Id",
                    "Version",
                    "Status",
                    "LastUpdateStatus",
                    "EndpointUrl");
            log.info("---\t---\t---\t---\t---");
        }
        log.info("{}\t{}\t{}\t{}\t{}",
                taskStatus.getTaskId(),
                taskStatus.getVersion(),
                taskStatus.getStatus(),
                Objects.toString(taskStatus.getLastUpdateStatus(), "N/A")
                + (Strings.isNullOrEmpty(taskStatus.getLastUpdateStatusReason()) ? "" : "(" + taskStatus.getLastUpdateStatusReason() + ")"),
                Objects.toString(taskStatus.getEndpointUrl(), "N/A"));
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

    @Override
    public io.dataspray.stream.control.client.model.SubmitQueryResponse submitQuery(
            Organization organization, Project project, String sqlQuery) {
        try {
            return DataSprayClient.get(organization.toAccess())
                    .query()
                    .submitQuery(
                            organization.getName(),
                            new io.dataspray.stream.control.client.model.SubmitQueryRequest()
                                    .sqlQuery(sqlQuery));
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to submit query", ex);
        }
    }

    @Override
    public io.dataspray.stream.control.client.model.QueryExecutionStatus getQueryStatus(
            Organization organization, Project project, String queryExecutionId) {
        try {
            return DataSprayClient.get(organization.toAccess())
                    .query()
                    .getQueryStatus(organization.getName(), queryExecutionId);
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to get query status", ex);
        }
    }

    @Override
    public io.dataspray.stream.control.client.model.QueryResultsResponse getQueryResults(
            Organization organization, Project project, String queryExecutionId,
            String nextToken, Integer maxResults) {
        try {
            return DataSprayClient.get(organization.toAccess())
                    .query()
                    .getQueryResults(
                            organization.getName(),
                            queryExecutionId,
                            nextToken,
                            maxResults);
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to get query results", ex);
        }
    }

    @Override
    public io.dataspray.stream.control.client.model.QueryHistoryResponse getQueryHistory(
            Organization organization, Project project, Integer maxResults) {
        try {
            return DataSprayClient.get(organization.toAccess())
                    .query()
                    .getQueryHistory(organization.getName(), maxResults);
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to get query history", ex);
        }
    }

    @Override
    public io.dataspray.stream.control.client.model.DatabaseSchemaResponse getDatabaseSchema(
            Organization organization, Project project) {
        try {
            return DataSprayClient.get(organization.toAccess())
                    .query()
                    .getDatabaseSchema(organization.getName());
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to get database schema", ex);
        }
    }

    @Override
    public io.dataspray.stream.control.client.model.TopicSchema getTopicSchema(
            Organization organization, Project project, String topicName) {
        try {
            return DataSprayClient.get(organization.toAccess())
                    .control()
                    .getTopicSchema(organization.getName(), topicName);
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to get topic schema for " + topicName, ex);
        }
    }

    @Override
    public io.dataspray.stream.control.client.model.TopicSchema recalculateTopicSchema(
            Organization organization, Project project, String topicName) {
        try {
            return DataSprayClient.get(organization.toAccess())
                    .control()
                    .recalculateTopicSchema(organization.getName(), topicName);
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to recalculate schema for topic " + topicName, ex);
        }
    }
}
