/*
 * Copyright 2023 Matus Faro
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
import com.google.common.collect.ImmutableSet;
import io.dataspray.store.LambdaDeployer;
import io.dataspray.store.LambdaDeployer.DeployedVersion;
import io.dataspray.store.LambdaDeployer.State;
import io.dataspray.store.LambdaDeployer.Status;
import io.dataspray.store.LambdaDeployer.Versions;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.TaskStatus.StatusEnum;
import io.dataspray.stream.control.model.TaskStatuses;
import io.dataspray.stream.control.model.TaskVersion;
import io.dataspray.stream.control.model.TaskVersions;
import io.dataspray.stream.control.model.UploadCodeRequest;
import io.dataspray.stream.control.model.UploadCodeResponse;
import io.dataspray.web.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;

import java.util.Optional;
import java.util.stream.Collectors;

import static io.dataspray.store.LambdaDeployer.UploadCodeClaim;
import static io.dataspray.stream.control.model.TaskStatus.StatusEnum.NOTFOUND;

@Slf4j
@ApplicationScoped
public class ControlResource extends AbstractResource implements ControlApi {

    @Inject
    LambdaDeployer deployer;

    @Override
    public TaskStatus activateVersion(String taskId, String version) {
        deployer.switchVersion(getCustomerId(), taskId, version);
        return getStatus(getCustomerId(), taskId);
    }

    @Override
    public TaskStatus delete(String taskId) {
        deployer.delete(getCustomerId(), taskId);
        return getStatus(getCustomerId(), taskId);
    }

    @Override
    public TaskVersion deployVersion(String taskId, DeployRequest deployRequest) {
        DeployedVersion deployedVersion = deployer.deployVersion(
                getCustomerId(),
                getCustomerApiKey(),
                taskId,
                deployRequest.getCodeUrl(),
                deployRequest.getHandler(),
                deployRequest.getInputQueueNames().stream()
                        .distinct()
                        .collect(ImmutableSet.toImmutableSet()),
                // TODO Switch this back to Runtime enum when Quarkus bumps AWS SDK version that supports JAVA21
                // Enums.getIfPresent(Runtime.class, deployRequest.getRuntime().name()).toJavaUtil()
                //         .orElseThrow(() -> new BadRequestException("Unknown runtime: " + deployRequest.getRuntime())),
                deployRequest.getRuntime().name(),
                deployRequest.getSwitchToNow());
        return new TaskVersion(
                taskId,
                deployedVersion.getVersion(),
                deployedVersion.getDescription());
    }

    @Override
    public TaskVersions getVersions(String taskId) {
        Versions versions = deployer.getVersions(getCustomerId(), taskId);
        return new TaskVersions(
                toTaskStatus(taskId, Optional.of(versions.getStatus())),
                versions.getTaskByVersion().values().stream()
                        .map(v -> new TaskVersion(
                                taskId,
                                v.getVersion(),
                                v.getDescription()))
                        .collect(Collectors.toList()));
    }

    @Override
    public TaskStatus pause(String taskId) {
        deployer.pause(getCustomerId(), taskId);
        return getStatus(getCustomerId(), taskId);
    }

    @Override
    public TaskStatus resume(String taskId) {
        deployer.resume(getCustomerId(), taskId);
        return getStatus(getCustomerId(), taskId);
    }

    @Override
    public TaskStatus status(String taskId) {
        return getStatus(getCustomerId(), taskId);
    }

    @Override
    public TaskStatuses statusAll() {
        return new TaskStatuses(deployer.statusAll(getCustomerId()).stream()
                .map(status -> toTaskStatus(status.getTaskId(), Optional.of(status)))
                .collect(Collectors.toList()));
    }

    @Override
    public UploadCodeResponse uploadCode(UploadCodeRequest uploadCodeRequest) {
        UploadCodeClaim uploadCodeClaim = deployer.uploadCode(getCustomerId(), uploadCodeRequest.getTaskId(), uploadCodeRequest.getContentLengthBytes());
        return new UploadCodeResponse(uploadCodeClaim.getPresignedUrl(), uploadCodeClaim.getCodeUrl());
    }

    private TaskStatus getStatus(String customerId, String taskId) {
        return toTaskStatus(taskId, deployer.status(customerId, taskId));
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
