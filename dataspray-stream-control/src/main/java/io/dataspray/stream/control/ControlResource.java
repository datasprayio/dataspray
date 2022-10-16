package io.dataspray.stream.control;

import com.google.common.base.Enums;
import com.google.common.collect.ImmutableSet;
import io.dataspray.lambda.resource.AbstractResource;
import io.dataspray.store.LambdaDeployer;
import io.dataspray.store.LambdaDeployer.DeployedVersion;
import io.dataspray.store.LambdaDeployer.Status;
import io.dataspray.store.LambdaDeployer.Versions;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.TaskStatuses;
import io.dataspray.stream.control.model.TaskVersion;
import io.dataspray.stream.control.model.TaskVersions;
import io.dataspray.stream.control.model.UploadCodeRequest;
import io.dataspray.stream.control.model.UploadCodeResponse;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.Runtime;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.dataspray.store.LambdaDeployer.UploadCodeClaim;
import static io.dataspray.stream.control.model.TaskStatus.StatusEnum.*;

@Slf4j
@ApplicationScoped
public class ControlResource extends AbstractResource implements ControlApi {

    @Inject
    LambdaDeployer deployer;

    // TODO get customer and setup billing
    private final String customerId = "matus";

    @Override
    public TaskStatus activateVersion(String taskId, String version) {
        deployer.switchVersion(customerId, taskId, version);
        return getStatus(customerId, taskId);
    }

    @Override
    public TaskStatus delete(String taskId) {
        deployer.delete(customerId, taskId);
        return getStatus(customerId, taskId);
    }

    @Override
    public TaskVersion deployVersion(String taskId, DeployRequest deployRequest) {
        DeployedVersion deployedVersion = deployer.deployVersion(
                customerId,
                taskId,
                deployRequest.getCodeUrl(),
                deployRequest.getInputQueueNames().stream()
                        .distinct()
                        .collect(ImmutableSet.toImmutableSet()),
                Enums.getIfPresent(Runtime.class, deployRequest.getRuntime().name()).toJavaUtil()
                        .orElseThrow(() -> new BadRequestException("Unknown runtime: " + deployRequest.getRuntime())));
        return new TaskVersion(
                taskId,
                deployedVersion.getVersion(),
                deployedVersion.getDescription());
    }

    @Override
    public TaskVersions getVersions(String taskId) {
        Versions versions = deployer.getVersions(customerId, taskId);
        return new TaskVersions(
                versions.getActive().orElse(null),
                versions.getTaskByVersion().values().stream()
                        .map(v -> new TaskVersion(
                                taskId,
                                v.getVersion(),
                                v.getDescription()))
                        .collect(Collectors.toList()));
    }

    @Override
    public TaskStatus pause(String taskId) {
        deployer.pause(customerId, taskId);
        return getStatus(customerId, taskId);
    }

    @Override
    public TaskStatus resume(String taskId) {
        deployer.resume(customerId, taskId);
        return getStatus(customerId, taskId);
    }

    @Override
    public TaskStatus status(String taskId) {
        return getStatus(customerId, taskId);
    }

    @Override
    public TaskStatuses statusAll() {
        return null;
    }

    @Override
    public UploadCodeResponse uploadCode(UploadCodeRequest uploadCodeRequest) {
        UploadCodeClaim uploadCodeClaim = deployer.uploadCode(customerId, uploadCodeRequest.getTaskId(), uploadCodeRequest.getContentLengthBytes());
        return new UploadCodeResponse(uploadCodeClaim.getPresignedUrl(), uploadCodeClaim.getCodeUrl());
    }

    private TaskStatus getStatus(String customerId, String taskId) {
        Optional<Status> statusOpt = deployer.status(customerId, taskId);
        return TaskStatus.builder()
                .taskId(taskId)
                .status(statusOpt.map(Status::isActive)
                        .map(isActive -> isActive ? RUNNING : PAUSED)
                        .orElse(MISSING))
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
                .build();
    }
}
