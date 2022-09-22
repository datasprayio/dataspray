package io.dataspray.stream.control;

import com.google.common.collect.ImmutableList;
import io.dataspray.common.aws.test.AwsTestProfile;
import io.dataspray.stream.client.StreamApi;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.TaskStatuses;
import io.dataspray.stream.control.model.UpdateRequest;
import io.dataspray.stream.control.model.UploadCodeRequest;
import io.dataspray.stream.control.model.UploadCodeResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@QuarkusTest
@TestProfile(AwsTestProfile.class)
public class ControlTest {

    @Inject
    ControlResource resource;
    @Inject
    StreamApi streamApi;

    @Test
    public void testStatus() throws Exception {
        UploadCodeResponse uploadCodeResponse = resource.uploadCode(UploadCodeRequest.builder()
                .taskId("task1")
                .contentLengthBytes(12L).build());
        log.info("uploadCodeResponse {}", uploadCodeResponse);

        streamApi.uploadCode(uploadCodeResponse.getPresignedUrl(),
                Files.writeString(Files.createTempFile(null, null), "test\n").toFile());

        assertEquals(
                TaskStatus.builder()
                        .taskId("task1")
                        .status(TaskStatus.StatusEnum.MISSING).build(),
                resource.status("task1"));

        assertEquals(
                TaskStatus.builder()
                        .taskId("task1")
                        .status(TaskStatus.StatusEnum.RUNNING).build(),
                resource.deploy(DeployRequest.builder()
                        .taskId("task1")
                        .codeUrl(uploadCodeResponse.getCodeUrl())
                        .runtime(DeployRequest.RuntimeEnum.JAVA11).build()));

        assertEquals(
                TaskStatus.builder()
                        .taskId("task1")
                        .status(TaskStatus.StatusEnum.PAUSED).build(),
                resource.pause("task1"));

        assertEquals(
                TaskStatus.builder()
                        .taskId("task1")
                        .status(TaskStatus.StatusEnum.RUNNING).build(),
                resource.resume("task1"));

        assertEquals(
                TaskStatus.builder()
                        .taskId("task1")
                        .status(TaskStatus.StatusEnum.RUNNING).build(),
                resource.update("task1", UpdateRequest.builder()
                        .codeUrl(uploadCodeResponse.getCodeUrl()).build()));

        assertEquals(
                TaskStatuses.builder()
                        .tasks(ImmutableList.of(
                                TaskStatus.builder()
                                        .taskId("task1")
                                        .status(TaskStatus.StatusEnum.RUNNING).build())).build(),
                resource.statusAll());

        assertEquals(
                TaskStatus.builder()
                        .taskId("task1")
                        .status(TaskStatus.StatusEnum.MISSING).build(),
                resource.delete("task1"));
    }
}
