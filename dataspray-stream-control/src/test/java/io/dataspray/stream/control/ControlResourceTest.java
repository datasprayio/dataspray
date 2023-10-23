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
import io.dataspray.common.authorizer.AuthorizerConstants;
import io.dataspray.common.test.aws.AwsTestProfile;
import io.dataspray.stream.client.StreamApi;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.TaskStatuses;
import io.dataspray.stream.control.model.TaskVersion;
import io.dataspray.stream.control.model.UploadCodeRequest;
import io.dataspray.stream.control.model.UploadCodeResponse;
import io.quarkus.amazon.lambda.http.model.ApiGatewayAuthorizerContext;
import io.quarkus.amazon.lambda.http.model.AwsProxyRequestContext;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@QuarkusTest
@TestProfile(AwsTestProfile.class)
public class ControlResourceTest {

    @Inject
    ControlResource resource;
    @Inject
    StreamApi streamApi;
    @Inject
    S3Client s3Client;

    @BeforeEach
    public void beforeEach() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket("io-dataspray-code-upload")
                    .build());
        } catch (BucketAlreadyOwnedByYouException ex) {
            // Already exists and is ours
        }
    }

    @Test
    public void test() throws Exception {
        String taskId = "task-control-resource-test";
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
                filterStatusAll(resource.statusAll(), taskId));

        assertEquals(
                TaskStatus.builder()
                        .taskId(taskId)
                        .status(TaskStatus.StatusEnum.NOTFOUND).build(),
                resource.delete(taskId));
    }

    /**
     * Filters response value to include only status from given task id
     */
    private static TaskStatuses filterStatusAll(TaskStatuses taskStatuses, String taskId) {
        return taskStatuses.toBuilder()
                .tasks(taskStatuses.getTasks().stream()
                        .filter(ts -> ts.getTaskId().equals(taskId))
                        .collect(ImmutableList.toImmutableList()))
                .build();
    }

    @Mock
    @ApplicationScoped
    public AwsProxyRequestContext getAwsProxyRequestContext() {
        AwsProxyRequestContext awsProxyRequestContext = new AwsProxyRequestContext();
        awsProxyRequestContext.setAuthorizer(new ApiGatewayAuthorizerContext());
        awsProxyRequestContext.getAuthorizer().setContextValue(AuthorizerConstants.CONTEXT_KEY_ACCOUNT_ID, "123");
        awsProxyRequestContext.getAuthorizer().setContextValue(AuthorizerConstants.CONTEXT_KEY_APIKEY_VALUE, "456");
        return awsProxyRequestContext;
    }
}
