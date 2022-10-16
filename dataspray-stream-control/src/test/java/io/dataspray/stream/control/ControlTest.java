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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
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
                        .status(TaskStatus.StatusEnum.MISSING).build(),
                resource.status(taskId));

        String queueName = "queue1";
        TaskVersion deployedVersion = resource.deployVersion(taskId, DeployRequest.builder()
                .codeUrl(uploadCodeResponse.getCodeUrl())
                .inputQueueNames(List.of(queueName))
                .runtime(DeployRequest.RuntimeEnum.JAVA11).build());
        log.info("Deployed version {}", deployedVersion);

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .status(TaskStatus.StatusEnum.RUNNING).build(),
                resource.activateVersion(taskId, deployedVersion.getVersion()));

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .status(TaskStatus.StatusEnum.PAUSED).build(),
                resource.pause(taskId));

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .status(TaskStatus.StatusEnum.RUNNING).build(),
                resource.resume(taskId));

        String queueName2 = "queue2";
        TaskVersion deployedVersion2 = resource.deployVersion(taskId, DeployRequest.builder()
                .codeUrl(uploadCodeResponse.getCodeUrl())
                .inputQueueNames(List.of(queueName2))
                .runtime(DeployRequest.RuntimeEnum.NODEJS14_X).build());
        log.info("Deployed another version {}", deployedVersion);

        assertEquals(
                TaskStatuses.builder()
                        .tasks(ImmutableList.of(
                                TaskStatus.builder()
                                        .taskId(taskId)
                                        .status(TaskStatus.StatusEnum.RUNNING).build())).build(),
                resource.statusAll());

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .status(TaskStatus.StatusEnum.MISSING).build(),
                resource.delete(taskId));
    }
}
