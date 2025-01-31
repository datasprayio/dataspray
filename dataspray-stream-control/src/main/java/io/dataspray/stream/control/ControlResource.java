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
import com.google.common.collect.Maps;
import io.dataspray.singletable.TableType;
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import io.dataspray.store.BatchStore;
import io.dataspray.store.JobStore;
import io.dataspray.store.JobStore.Session;
import io.dataspray.store.LambdaDeployer;
import io.dataspray.store.LambdaDeployer.DeployedVersion;
import io.dataspray.store.LambdaDeployer.Endpoint;
import io.dataspray.store.LambdaDeployer.State;
import io.dataspray.store.LambdaDeployer.Status;
import io.dataspray.store.LambdaDeployer.Versions;
import io.dataspray.store.LambdaStore;
import io.dataspray.store.OrganizationStore;
import io.dataspray.store.TopicStore;
import io.dataspray.store.util.WithCursor;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.DeployVersionCheckResponse;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.TaskStatus.StatusEnum;
import io.dataspray.stream.control.model.TaskStatuses;
import io.dataspray.stream.control.model.TaskVersion;
import io.dataspray.stream.control.model.TaskVersions;
import io.dataspray.stream.control.model.Topic;
import io.dataspray.stream.control.model.TopicBatch;
import io.dataspray.stream.control.model.TopicStoreKey;
import io.dataspray.stream.control.model.TopicStoreKey.TableTypeEnum;
import io.dataspray.stream.control.model.TopicStream;
import io.dataspray.stream.control.model.Topics;
import io.dataspray.stream.control.model.UpdateDefaultTopicRequest;
import io.dataspray.stream.control.model.UploadCodeRequest;
import io.dataspray.stream.control.model.UploadCodeResponse;
import io.dataspray.web.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
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
    OrganizationStore organizationStore;
    @Inject
    BatchStore batchStore;
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
    public TaskVersion deployVersion(String organizationName, String taskId, String sessionId, String invocationType, DeployRequest deployRequest) {
        log.info("Deploying task {} org {} session {} invocationType {}", taskId, organizationName, sessionId, invocationType);
        Session session = jobStore.startSession(sessionId);
        try {
            UsageKeyType apiAccessKeyType = organizationStore.getMetadata(organizationName)
                    .getUsageKeyType();
            DeployedVersion deployedVersion = deployer.deployVersion(
                    organizationName,
                    getUsername().orElseThrow(),
                    apiAccessKeyType,
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
            TaskVersion taskVersion = new TaskVersion(
                    taskId,
                    deployedVersion.getVersion(),
                    deployedVersion.getDescription());

            jobStore.success(sessionId, taskVersion);
            return taskVersion;
        } catch (WebApplicationException ex) {
            jobStore.failure(sessionId, ex.getResponse().getStatus() + ": " + ex.getMessage());
            log.warn("Failed to deploy; org {} task {} session {}", organizationName, taskId, sessionId, ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Unknown error deploying; org {} task {} session {}", organizationName, taskId, sessionId, ex);
            jobStore.failure(sessionId, "Unknown failure: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public DeployVersionCheckResponse deployVersionCheck(String organizationName, String taskId, String sessionId) {

        Optional<Session> sessionOpt = jobStore.check(sessionId);

        if (sessionOpt.isEmpty()) {
            return new DeployVersionCheckResponse(
                    DeployVersionCheckResponse.StatusEnum.NOTFOUND,
                    "Session not found",
                    null);
        }
        return switch (sessionOpt.get().getState()) {
            case PENDING -> new DeployVersionCheckResponse(
                    DeployVersionCheckResponse.StatusEnum.PROCESSING,
                    "Has not started yet",
                    null);
            case PROCESSING -> new DeployVersionCheckResponse(
                    DeployVersionCheckResponse.StatusEnum.PROCESSING,
                    "Still processing",
                    null);
            case SUCCESS -> new DeployVersionCheckResponse(
                    DeployVersionCheckResponse.StatusEnum.SUCCESS,
                    null,
                    sessionOpt.get().getResult(TaskVersion.class));
            case FAILURE -> new DeployVersionCheckResponse(
                    DeployVersionCheckResponse.StatusEnum.FAILED,
                    sessionOpt.get().getError(),
                    null);
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
    public Topics getTopics(String organizationName) {
        TopicStore.Topics topics = topicStore.getTopics(organizationName, true);
        return modelToTargets(organizationName, topics);
    }

    @Override
    public Topics updateDefaultTopic(String organizationName, UpdateDefaultTopicRequest updateDefaultTopicRequest, Long expectVersion) {
        return modelToTopics(topicStore.updateDefaultTopic(organizationName, Optional.ofNullable(updateDefaultTopicRequest.getTopic()).map(this::topicToModel), updateDefaultTopicRequest.getAllowUndefined(), Optional.ofNullable(expectVersion)));
    }

    @Override
    public Topics updateTopic(String organizationName, String topicName, Topic topic, Long expectVersion) {
        return modelToTopics(topicStore.updateTopic(organizationName, topicName, topicToModel(topic), Optional.ofNullable(expectVersion)));
    }

    @Override
    public Topics deleteTopic(String organizationName, String topicName, Long expectVersion) {
        return modelToTopics(topicStore.deleteTopic(organizationName, topicName, Optional.ofNullable(expectVersion)));
    }

    @Override
    public UploadCodeResponse uploadCode(String organizationName, UploadCodeRequest uploadCodeRequest) {
        UploadCodeClaim uploadCodeClaim = deployer.uploadCode(organizationName, uploadCodeRequest.getTaskId(), uploadCodeRequest.getContentLengthBytes());
        return new UploadCodeResponse(
                jobStore.createSession().getSessionId(),
                uploadCodeClaim.getPresignedUrl(),
                uploadCodeClaim.getCodeUrl());
    }

    private TopicStore.Topic topicToModel(Topic topic) {
        return new TopicStore.Topic(
                Optional.ofNullable(topic.getBatch())
                        .map(batch -> new TopicStore.Batch(
                                Optional.ofNullable(batch.getRetentionInDays())
                                        .map(TopicStore.BatchRetention::fromDays)
                                        .orElse(null)))
                        .orElse(null),
                topic.getStreams() == null ? ImmutableList.of() : topic.getStreams().stream()
                        .map(stream -> new TopicStore.Stream(stream.getName()))
                        .collect(ImmutableList.toImmutableList()),
                Optional.ofNullable(topic.getStore()).map(store -> new TopicStore.Store(
                                store.getKeys().stream()
                                        .map(key -> new TopicStore.Key(
                                                switch (key.getTableType()) {
                                                    case PRIMARY -> TableType.Primary;
                                                    case GSI -> TableType.Gsi;
                                                    case LSI -> TableType.Lsi;
                                                    default ->
                                                            throw new IllegalArgumentException("Unknown table type: " + key.getTableType());
                                                },
                                                key.getIndexNumber() == null ? 0 : key.getIndexNumber(),
                                                ImmutableList.copyOf(key.getPkParts()),
                                                key.getSkParts() == null ? ImmutableList.of() : ImmutableList.copyOf(key.getSkParts()),
                                                key.getRangePrefix()))
                                        .collect(ImmutableSet.toImmutableSet()),
                                store.getTtlInSec(),
                                store.getBlacklist() == null ? ImmutableSet.of() : ImmutableSet.copyOf(store.getBlacklist()),
                                store.getWhitelist() == null ? ImmutableSet.of() : ImmutableSet.copyOf(store.getWhitelist())))
                        .orElse(null));
    }

    private Topics modelToTargets(String organizationName, TopicStore.Topics topics) {
        return new Topics(
                organizationName,
                Boolean.TRUE.equals(topics.getAllowUndefinedTopics()),
                Optional.ofNullable(topics.getUndefinedTopic())
                        .map(this::modelToTopic)
                        .orElse(null),
                Maps.transformValues(topics.getTopics(), this::modelToTopic),
                topics.getVersion());
    }

    private Topics modelToTopics(TopicStore.Topics topics) {
        return new Topics(
                topics.getOrganizationName(),
                topics.getAllowUndefinedTopics(),
                Optional.ofNullable(topics.getUndefinedTopic())
                        .map(this::modelToTopic)
                        .orElse(null),
                Maps.transformValues(topics.getTopics(), this::modelToTopic),
                topics.getVersion());
    }

    private Topic modelToTopic(TopicStore.Topic topic) {
        return new Topic(
                topic.getBatch()
                        .map(batch -> new TopicBatch(batch.getRetention().getRetentionInDays()))
                        .orElse(null),
                topic.getStreams().stream()
                        .map(stream -> new TopicStream(stream.getName()))
                        .collect(ImmutableList.toImmutableList()),
                topic.getStore()
                        .map(store -> new io.dataspray.stream.control.model.TopicStore(
                                store.getKeys().stream()
                                        .map(key -> new TopicStoreKey(
                                                switch (key.getType()) {
                                                    case Primary -> TableTypeEnum.PRIMARY;
                                                    case Gsi -> TableTypeEnum.GSI;
                                                    case Lsi -> TableTypeEnum.LSI;
                                                },
                                                key.getIndexNumber(),
                                                key.getPkParts(),
                                                key.getSkParts(),
                                                key.getRangePrefix()))
                                        .collect(ImmutableList.toImmutableList()),
                                store.getTtlInSec(),
                                store.getBlacklist().asList(),
                                store.getWhitelist().asList()))
                        .orElse(null));
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
                .inputQueueNames(statusOpt
                        .map(Status::getRecord)
                        .map(LambdaStore.LambdaRecord::getInputQueueNames)
                        .map(ImmutableSet::asList)
                        .orElse(null))
                .outputQueueNames(statusOpt
                        .map(Status::getRecord)
                        .map(LambdaStore.LambdaRecord::getOutputQueueNames)
                        .map(ImmutableSet::asList)
                        .orElse(null))
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
