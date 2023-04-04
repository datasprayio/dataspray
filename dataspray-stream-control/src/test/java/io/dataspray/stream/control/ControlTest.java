package io.dataspray.stream.control;

import com.google.common.collect.ImmutableList;
import io.dataspray.common.aws.test.AwsTestProfile;
import io.dataspray.stream.client.StreamApi;
import io.dataspray.stream.control.deploy.MockControlStack;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.TaskStatuses;
import io.dataspray.stream.control.model.TaskVersion;
import io.dataspray.stream.control.model.UploadCodeRequest;
import io.dataspray.stream.control.model.UploadCodeResponse;
import io.findify.s3mock.S3Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@QuarkusTest
@TestProfile(AwsTestProfile.class)
public class ControlTest {

    @Inject
    ControlResource resource;
    @Inject
    StreamApi streamApi;

    /** Injected via MockS3Client */
    S3Mock s3Mock;

    @BeforeEach
    public void beforeEach() {
        MockControlStack.mock(s3Mock);
    }

    @Test
    public void test() throws Exception {
        String taskId = "task1";
        UploadCodeResponse uploadCodeResponse = resource.uploadCode(UploadCodeRequest.builder()
                .taskId(taskId)
                .contentLengthBytes(12L).build());
        log.info("uploadCodeResponse {}", uploadCodeResponse);

        streamApi.uploadCode(uploadCodeResponse.getPresignedUrl(),
                Files.writeString(Files.createTempFile(null, null), "test\n").toFile());

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .status(TaskStatus.StatusEnum.NOTFOUND).build(),
                resource.status(taskId));

        String queueName = "queue1";
        TaskVersion deployedVersion = resource.deployVersion(taskId, DeployRequest.builder()
                .codeUrl(uploadCodeResponse.getCodeUrl())
                .handler("io.dataspray.Runner")
                .inputQueueNames(List.of(queueName))
                .runtime(DeployRequest.RuntimeEnum.JAVA11)
                .switchToNow(false).build());
        log.info("Deployed version {}", deployedVersion);

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .version("1")
                        .status(TaskStatus.StatusEnum.RUNNING)
                        .lastUpdateStatus(TaskStatus.LastUpdateStatusEnum.SUCCESSFUL).build(),
                resource.activateVersion(taskId, deployedVersion.getVersion()));

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .version("1")
                        .status(TaskStatus.StatusEnum.PAUSED)
                        .lastUpdateStatus(TaskStatus.LastUpdateStatusEnum.SUCCESSFUL).build(),
                resource.pause(taskId));

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .version("1")
                        .status(TaskStatus.StatusEnum.RUNNING)
                        .lastUpdateStatus(TaskStatus.LastUpdateStatusEnum.SUCCESSFUL).build(),
                resource.resume(taskId));

        String queueName2 = "queue2";
        TaskVersion deployedVersion2 = resource.deployVersion(taskId, DeployRequest.builder()
                .codeUrl(uploadCodeResponse.getCodeUrl())
                .handler("io.dataspray.Runner")
                .inputQueueNames(List.of(queueName2))
                .runtime(DeployRequest.RuntimeEnum.NODEJS14_X)
                .switchToNow(true).build());
        log.info("Deployed another version {}", deployedVersion2);

        assertEquals(
                TaskStatuses.builder()
                        .tasks(ImmutableList.of(
                                TaskStatus.builder()
                                        .taskId(taskId)
                                        .version("2")
                                        .status(TaskStatus.StatusEnum.RUNNING)
                                        .lastUpdateStatus(TaskStatus.LastUpdateStatusEnum.SUCCESSFUL).build())).build(),
                resource.statusAll());

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .status(TaskStatus.StatusEnum.NOTFOUND).build(),
                resource.delete(taskId));
    }
}
