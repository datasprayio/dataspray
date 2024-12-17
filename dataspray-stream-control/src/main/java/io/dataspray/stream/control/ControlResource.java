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

package io.dataspray.stream.control;

import com.google.common.base.Enums;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.store.JobStore;
import io.dataspray.store.JobStore.Session;
import io.dataspray.store.LambdaDeployer;
import io.dataspray.store.LambdaDeployer.DeployedVersion;
import io.dataspray.store.LambdaDeployer.Endpoint;
import io.dataspray.store.LambdaDeployer.State;
import io.dataspray.store.LambdaDeployer.Status;
import io.dataspray.store.LambdaDeployer.Versions;
import io.dataspray.store.TopicStore;
import io.dataspray.store.util.WithCursor;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.TaskStatus.StatusEnum;
import io.dataspray.stream.control.model.TaskStatuses;
import io.dataspray.stream.control.model.TaskVersion;
import io.dataspray.stream.control.model.TaskVersions;
import io.dataspray.stream.control.model.Topic;
import io.dataspray.stream.control.model.TopicBatch;
import io.dataspray.stream.control.model.TopicStream;
import io.dataspray.stream.control.model.Topics;
import io.dataspray.stream.control.model.UploadCodeRequest;
import io.dataspray.stream.control.model.UploadCodeResponse;
import io.dataspray.web.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.lambda.model.Cors;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.util.Optional;
import java.util.stream.Collectors;

import static io.dataspray.store.LambdaDeployer.UploadCodeClaim;
import static io.dataspray.stream.control.model.TaskStatus.StatusEnum.NOTFOUND;

@Slf4j
@ApplicationScoped
public class ControlResource extends AbstractResource implements ControlApi {

    public static final String DATASPRAY_API_ENDPOINT_PROP_NAME = "dataspray.api.endpoint";
    @ConfigProperty(name = DATASPRAY_API_ENDPOINT_PROP_NAME)
    Optional<String> datasprayApiEndpoint;

    @Inject
    LambdaDeployer deployer;
    @Inject
    TopicStore topicStore;
    @Inject
    JobStore jobStore;

    @Override
    public TaskStatus activateVersion(String organizationName, String taskId, String version) {
        deployer.switchVersion(organizationName, taskId, version);
        return getStatus(organizationName, taskId);
    }

    @Override
    public TaskStatus delete(String organizationName, String taskId) {
        deployer.delete(organizationName, taskId);
        return getStatus(organizationName, taskId);
    }

    @Override
    public void deployVersion(String organizationName, String taskId, String sessionId, DeployRequest deployRequest) {
        log.info("Deploying task {} org {} session {}", taskId, organizationName, sessionId);
        Session session = jobStore.startSession(sessionId);
        try {
            DeployedVersion deployedVersion = deployer.deployVersion(
                    organizationName,
                    getUsername().orElseThrow(),
                    datasprayApiEndpoint,
                    taskId,
                    deployRequest.getCodeUrl(),
                    deployRequest.getHandler(),
                    deployRequest.getInputQueueNames().stream()
                            .distinct()
                            .collect(ImmutableSet.toImmutableSet()),
                    deployRequest.getOutputQueueNames().stream()
                            .distinct()
                            .collect(ImmutableSet.toImmutableSet()),
                    Enums.getIfPresent(Runtime.class, deployRequest.getRuntime().name()).toJavaUtil()
                            .orElseThrow(() -> new BadRequestException("Unknown runtime: " + deployRequest.getRuntime())),
                    Optional.ofNullable(deployRequest.getEndpoint())
                            .map(endpoint -> new Endpoint(
                                    endpoint.getIsPublic(),
                                    Optional.ofNullable(endpoint.getCors())
                                            .map(cors -> Cors.builder()
                                                    .allowOrigins(cors.getAllowOrigins())
                                                    .allowMethods(cors.getAllowMethods())
                                                    .allowHeaders(cors.getAllowHeaders())
                                                    .exposeHeaders(cors.getExposeHeaders())
                                                    .allowCredentials(cors.getAllowCredentials())
                                                    .maxAge(cors.getMaxAge().intValue())
                                                    .build()))),
                    Optional.ofNullable(deployRequest.getDynamoState()).map(state -> new LambdaDeployer.DynamoState(state.getLsiCount(), state.getGsiCount())),
                    deployRequest.getSwitchToNow());

            log.info("Deployed task {} org {} version {} description {}",
                    taskId, organizationName, deployedVersion.getVersion(), deployedVersion.getDescription());
            jobStore.success(sessionId, new TaskVersion(
                    taskId,
                    deployedVersion.getVersion(),
                    deployedVersion.getDescription()));
        } catch (WebApplicationException ex) {
            jobStore.failure(sessionId, ex.getResponse().getStatus() + ": " + ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            jobStore.failure(sessionId, "Unknown failure: " + ex.getMessage());
        }
    }

    @Override
    public TaskVersion deployVersionCheck(@NotNull String organizationName, @NotNull String taskId, @NotNull String sessionId) {
        Optional<Session> sessionOpt = jobStore.check(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new NotFoundException("Session not found or timed out");
        }
        return switch (sessionOpt.get().getState()) {
            case PENDING, PROCESSING -> throw new WebApplicationException(202);
            case SUCCESS -> sessionOpt.get().getResult(TaskVersion.class);
            case FAILURE -> throw new InternalServerErrorException(sessionOpt.get().getError());
        };
    }

    @Override
    public TaskVersions getVersions(String organizationName, String taskId) {
        Versions versions = deployer.getVersions(organizationName, taskId);
        return new TaskVersions(
                toTaskStatus(taskId, Optional.of(versions.getStatus())),
                versions.getTaskByVersion().values().stream()
                        .map(v -> new TaskVersion(
                                taskId,
                                v.getVersion(),
                                v.getDescription()))
                        .collect(Collectors.toList()),
                versions.getEndpointUrl().orElse(null));
    }

    @Override
    public TaskStatus pause(String organizationName, String taskId) {
        deployer.pause(organizationName, taskId);
        return getStatus(organizationName, taskId);
    }

    @Override
    public TaskStatus resume(String organizationName, String taskId) {
        deployer.resume(organizationName, taskId);
        return getStatus(organizationName, taskId);
    }

    @Override
    public TaskStatus status(String organizationName, String taskId) {
        return getStatus(organizationName, taskId);
    }

    @Override
    public TaskStatuses statusAll(String organizationName, String cursor) {
        WithCursor<ImmutableList<Status>> result = deployer.statusAll(organizationName, cursor);
        return new TaskStatuses(
                result.getData().stream()
                        .map(status -> toTaskStatus(status.getTaskId(), Optional.of(status)))
                        .collect(Collectors.toList()),
                result.getCursorOpt().orElse(null));
    }

    @Override
    public UploadCodeResponse uploadCode(String organizationName, UploadCodeRequest uploadCodeRequest) {
        UploadCodeClaim uploadCodeClaim = deployer.uploadCode(organizationName, uploadCodeRequest.getTaskId(), uploadCodeRequest.getContentLengthBytes());
        return new UploadCodeResponse(
                jobStore.createSession().getSessionId(),
                uploadCodeClaim.getPresignedUrl(),
                uploadCodeClaim.getCodeUrl());
    }

    @Override
    public Topics getTopics(String organizationName) {
        TopicStore.Topics topics = topicStore.getTopics(organizationName, true);
        return modelToTargets(organizationName, topics);
    }

    private Topics modelToTargets(String organizationName, TopicStore.Topics topics) {
        return new Topics(
                organizationName,
                Boolean.TRUE.equals(topics.getAllowUndefinedTopics()),
                Optional.ofNullable(topics.getUndefinedTopic()).map(this::modelToTopic).orElse(null),
                topics.getTopics().stream()
                        .map(this::modelToTopic)
                        .collect(ImmutableList.toImmutableList()));
    }


    private Topic modelToTopic(TopicStore.Topic topic) {
        return new Topic(
                topic.getName(),
                topic.getBatch()
                        .map(batch -> new TopicBatch(batch.getRetention().getRetentionInDays()))
                        .orElse(null),
                topic.getStreams().stream()
                        .map(stream -> new TopicStream(stream.getName()))
                        .collect(ImmutableList.toImmutableList()));
    }

    private TaskStatus getStatus(String organizationName, String taskId) {
        return toTaskStatus(taskId, deployer.status(organizationName, taskId));
    }

    private TaskStatus toTaskStatus(String taskId, Optional<Status> statusOpt) {
        return TaskStatus.builder()
                .taskId(taskId)
                .version(statusOpt.map(Status::getFunction).map(FunctionConfiguration::version).orElse(null))
                .status(statusOpt.map(Status::getState)
                        .map(this::stateToTaskStatusEnum)
                        .orElse(NOTFOUND))
                .lastUpdateStatusReason(statusOpt
                        .map(Status::getFunction)
                        .map(FunctionConfiguration::lastUpdateStatusReason)
                        .orElse(null))
                .lastUpdateStatus(statusOpt
                        .map(Status::getFunction)
                        .map(FunctionConfiguration::lastUpdateStatus)
                        .map(Enum::name)
                        .flatMap(lastUpdateStatus -> Enums.getIfPresent(TaskStatus.LastUpdateStatusEnum.class, lastUpdateStatus).toJavaUtil())
                        .orElse(null))
                .endpointUrl(statusOpt.flatMap(Status::getEndpointUrlOpt).orElse(null))
                .build();
    }

    private StatusEnum stateToTaskStatusEnum(State state) {
        switch (state) {
            case STARTING:
                return StatusEnum.STARTING;
            case RUNNING:
                return StatusEnum.RUNNING;
            case PAUSING:
                return StatusEnum.PAUSING;
            case PAUSED:
                return StatusEnum.PAUSED;
            case UPDATING:
                return StatusEnum.UPDATING;
            case CREATING:
                return StatusEnum.CREATING;
            default:
                log.error("Unknown state {}", state);
                throw new InternalServerErrorException("Unknown state: " + state);
        }
    }
}
