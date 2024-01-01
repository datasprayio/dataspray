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

import com.google.common.collect.ImmutableList;
import io.dataspray.common.test.aws.AbstractLambdaTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.stream.client.StreamApiImpl;
import io.dataspray.stream.control.client.model.DeployRequest;
import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskStatuses;
import io.dataspray.stream.control.client.model.TaskVersion;
import io.dataspray.stream.control.client.model.UploadCodeRequest;
import io.dataspray.stream.control.client.model.UploadCodeResponse;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@QuarkusTestResource(MotoLifecycleManager.class)
public abstract class ControlBase extends AbstractLambdaTest {

    protected abstract S3Client getS3Client();

    @Test
    public void test() throws Exception {

        // Setup code bucket
        try {
            getS3Client().createBucket(CreateBucketRequest.builder()
                    .bucket("io-dataspray-code-upload")
                    .build());
        } catch (BucketAlreadyOwnedByYouException ex) {
            // Already exists and is ours
        }

        // Upload code request
        String taskId = "task1";
        UploadCodeResponse uploadCodeResponse = request(UploadCodeResponse.class, Given.builder()
                .method(HttpMethod.PUT)
                .path("/organization/" + getOrganizationName() + "/control/code/upload")
                .body(new UploadCodeRequest()
                        .taskId(taskId)
                        .contentLengthBytes(12L))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertTrue(uploadCodeResponse.getCodeUrl().startsWith("s3://io-dataspray-code-upload/customer/" + getOrganizationName() + "/task1-"), uploadCodeResponse.getCodeUrl());

        // Upload to S3
        new StreamApiImpl().uploadCode(
                uploadCodeResponse.getPresignedUrl(),
                Files.writeString(Files.createTempFile(null, null), "test\n").toFile());

        // Deploy version
        TaskVersion taskVersion = request(TaskVersion.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/organization/" + getOrganizationName() + "/control/task/" + taskId + "/deploy")
                .body(new DeployRequest()
                        .codeUrl(uploadCodeResponse.getCodeUrl())
                        .handler("io.dataspray.Runner")
                        .inputQueueNames(List.of("queue1"))
                        .runtime(DeployRequest.RuntimeEnum.JAVA11)
                        .switchToNow(false))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(taskId, taskVersion.getTaskId());
        assertEquals("1", taskVersion.getVersion());

        // Activate version
        TaskStatus taskStatusActivate = request(TaskStatus.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/organization/" + getOrganizationName() + "/control/task/" + taskId + "/activate")
                .query(Map.of("version", List.of("1")))
                .body(new DeployRequest()
                        .codeUrl(uploadCodeResponse.getCodeUrl())
                        .handler("io.dataspray.Runner")
                        .inputQueueNames(List.of("queue1"))
                        .runtime(DeployRequest.RuntimeEnum.JAVA11)
                        .switchToNow(false))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(taskId, taskStatusActivate.getTaskId());
        assertEquals("1", taskStatusActivate.getVersion());
        assertEquals(TaskStatus.StatusEnum.RUNNING, taskStatusActivate.getStatus());

        // Pause
        TaskStatus taskStatusPause = request(TaskStatus.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/organization/" + getOrganizationName() + "/control/task/" + taskId + "/pause")
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(taskId, taskStatusPause.getTaskId());
        assertEquals("1", taskStatusPause.getVersion());
        assertEquals(TaskStatus.StatusEnum.PAUSED, taskStatusPause.getStatus());

        // Resume
        TaskStatus taskStatusResume = request(TaskStatus.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/organization/" + getOrganizationName() + "/control/task/" + taskId + "/resume")
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(taskId, taskStatusResume.getTaskId());
        assertEquals("1", taskStatusResume.getVersion());
        assertEquals(TaskStatus.StatusEnum.RUNNING, taskStatusResume.getStatus());

        // Upload code request
        // Need to upload different code for published version to increment
        UploadCodeResponse uploadCodeResponse2 = request(UploadCodeResponse.class, Given.builder()
                .method(HttpMethod.PUT)
                .path("/organization/" + getOrganizationName() + "/control/code/upload")
                .body(new UploadCodeRequest()
                        .taskId(taskId)
                        .contentLengthBytes(12L))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertNotEquals(uploadCodeResponse.getCodeUrl(), uploadCodeResponse2.getCodeUrl());
        assertNotEquals(uploadCodeResponse.getPresignedUrl(), uploadCodeResponse2.getPresignedUrl());

        // Upload to S3
        new StreamApiImpl().uploadCode(
                uploadCodeResponse2.getPresignedUrl(),
                Files.writeString(Files.createTempFile(null, null), "TEST\n").toFile());

        // Deploy version
        TaskVersion taskVersion2 = request(TaskVersion.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/organization/" + getOrganizationName() + "/control/task/" + taskId + "/deploy")
                .body(new DeployRequest()
                        .codeUrl(uploadCodeResponse2.getCodeUrl())
                        .handler("io.dataspray.Runner")
                        .inputQueueNames(List.of("queue2"))
                        .runtime(DeployRequest.RuntimeEnum.NODEJS14_X)
                        .switchToNow(true))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(taskId, taskVersion2.getTaskId());
        assertEquals("2", taskVersion2.getVersion());

        // Status all
        TaskStatuses taskStatuses = request(TaskStatuses.class, Given.builder()
                .method(HttpMethod.GET)
                .path("/organization/" + getOrganizationName() + "/control/status")
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(new TaskStatuses()
                        .tasks(ImmutableList.of(
                                new TaskStatus()
                                        .taskId(taskId)
                                        .version("2")
                                        .status(TaskStatus.StatusEnum.RUNNING)
                                        .lastUpdateStatus(TaskStatus.LastUpdateStatusEnum.SUCCESSFUL))),
                filterStatusAll(taskStatuses, taskId));

        // Delete
        TaskStatus taskStatusDelete = request(TaskStatus.class, Given.builder()
                .method(HttpMethod.DELETE)
                .path("/organization/" + getOrganizationName() + "/control/task/" + taskId + "/delete")
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(taskId, taskStatusDelete.getTaskId());
        assertNull(taskStatusDelete.getVersion());
        assertEquals(TaskStatus.StatusEnum.NOTFOUND, taskStatusDelete.getStatus());
    }

    /**
     * Filters response value to include only status from given task id
     */
    private static TaskStatuses filterStatusAll(TaskStatuses taskStatuses, String taskId) {
        return taskStatuses
                .tasks(taskStatuses.getTasks().stream()
                        .filter(ts -> ts.getTaskId().equals(taskId))
                        .collect(ImmutableList.toImmutableList()));
    }
}
