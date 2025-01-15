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

import com.google.common.collect.ImmutableList;
import io.dataspray.client.Access;
import io.dataspray.client.DataSprayClient;
import io.dataspray.client.DataSprayClientImpl;
import io.dataspray.common.json.GsonUtil;
import io.dataspray.common.test.aws.AbstractLambdaTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import io.dataspray.store.OrganizationStore.OrganizationMetadata;
import io.dataspray.stream.control.client.model.DeployRequest;
import io.dataspray.stream.control.client.model.DeployRequestEndpoint;
import io.dataspray.stream.control.client.model.DeployRequestEndpointCors;
import io.dataspray.stream.control.client.model.DeployVersionCheckResponse;
import io.dataspray.stream.control.client.model.DeployVersionCheckResponse.StatusEnum;
import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.TaskStatuses;
import io.dataspray.stream.control.client.model.TaskVersion;
import io.dataspray.stream.control.client.model.UploadCodeRequest;
import io.dataspray.stream.control.client.model.UploadCodeResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateGroupRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.dataspray.common.test.aws.MotoLifecycleManager.CREATE_COGNITO_PARAM;
import static io.dataspray.store.impl.CognitoUserStore.USER_POOL_APP_CLIENT_ID_PROP_NAME;
import static io.dataspray.store.impl.CognitoUserStore.USER_POOL_ID_PROP_NAME;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@QuarkusTestResource(
        value = MotoLifecycleManager.class,
        initArgs = @ResourceArg(name = CREATE_COGNITO_PARAM, value = "true"))
public abstract class ControlBase extends AbstractLambdaTest {

    protected abstract S3Client getS3Client();

    protected abstract CognitoIdentityProviderClient getCognitoClient();

    @ConfigProperty(name = USER_POOL_ID_PROP_NAME)
    String userPoolId;
    @ConfigProperty(name = USER_POOL_APP_CLIENT_ID_PROP_NAME)
    String userPoolClientId;

    @BeforeEach
    public void beforeEach() {
        getCognitoClient().createGroup(CreateGroupRequest.builder()
                .userPoolId(userPoolId)
                .groupName(getOrganizationName())
                .description(GsonUtil.get().toJson(new OrganizationMetadata("authorUser", UsageKeyType.ORGANIZATION_TEN_RPS)))
                .build());

    }

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
                .path("/v1/organization/" + getOrganizationName() + "/control/code/upload")
                .body(new UploadCodeRequest()
                        .taskId(taskId)
                        .contentLengthBytes(12L))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertTrue(uploadCodeResponse.getCodeUrl().startsWith("s3://io-dataspray-code-upload/customer/" + getOrganizationName() + "/task1-"), uploadCodeResponse.getCodeUrl());

        // Check status
        DeployVersionCheckResponse status = request(DeployVersionCheckResponse.class, Given.builder()
                .method(HttpMethod.GET)
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/deploy/" + uploadCodeResponse.getSessionId()).build())
                .assertStatusCode(200)
                .getBody();
        assertEquals(StatusEnum.PROCESSING, status.getStatus());

        // Upload to S3
        ((DataSprayClientImpl) DataSprayClient.get(new Access("", Optional.empty()))).uploadToS3(
                uploadCodeResponse.getPresignedUrl(),
                Files.writeString(Files.createTempFile(null, null), "test\n").toFile());

        // Deploy version
        TaskVersion taskVersion = request(TaskVersion.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/deploy/" + uploadCodeResponse.getSessionId())
                .body(new DeployRequest()
                        .codeUrl(uploadCodeResponse.getCodeUrl())
                        .handler("io.dataspray.Runner")
                        .inputQueueNames(List.of("queue1"))
                        .runtime(DeployRequest.RuntimeEnum.JAVA21)
                        .endpoint(new DeployRequestEndpoint()
                                .isPublic(true)
                                .cors(new DeployRequestEndpointCors()
                                        .allowOrigins(List.of("*"))
                                        .allowMethods(List.of("GET", "POST"))
                                        .allowHeaders(List.of("Content-Type"))
                                        .exposeHeaders(List.of("Content-Type"))
                                        .allowCredentials(true)
                                        .maxAge(3600L)))
                        .switchToNow(false))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertNotNull(taskVersion);
        assertEquals(taskId, taskVersion.getTaskId());
        assertEquals("1", taskVersion.getVersion());

        // Check status
        status = request(DeployVersionCheckResponse.class, Given.builder()
                .method(HttpMethod.GET)
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/deploy/" + uploadCodeResponse.getSessionId()).build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(StatusEnum.SUCCESS, status.getStatus());
        taskVersion = status.getResult();
        assertNotNull(taskVersion);
        assertEquals(taskId, taskVersion.getTaskId());
        assertEquals("1", taskVersion.getVersion());

        // Activate version
        TaskStatus taskStatusActivate = request(TaskStatus.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/activate")
                .query(Map.of("version", List.of("1")))
                .body(new DeployRequest()
                        .codeUrl(uploadCodeResponse.getCodeUrl())
                        .handler("io.dataspray.Runner")
                        .inputQueueNames(List.of("queue1"))
                        .runtime(DeployRequest.RuntimeEnum.JAVA21)
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
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/pause")
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(taskId, taskStatusPause.getTaskId());
        assertEquals("1", taskStatusPause.getVersion());
        assertEquals(TaskStatus.StatusEnum.PAUSED, taskStatusPause.getStatus());

        // Resume
        TaskStatus taskStatusResume = request(TaskStatus.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/resume")
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
                .path("/v1/organization/" + getOrganizationName() + "/control/code/upload")
                .body(new UploadCodeRequest()
                        .taskId(taskId)
                        .contentLengthBytes(12L))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertNotEquals(uploadCodeResponse.getCodeUrl(), uploadCodeResponse2.getCodeUrl());
        assertNotEquals(uploadCodeResponse.getPresignedUrl(), uploadCodeResponse2.getPresignedUrl());

        // Upload to S3
        ((DataSprayClientImpl) DataSprayClient.get(new Access("", Optional.empty()))).uploadToS3(
                uploadCodeResponse2.getPresignedUrl(),
                Files.writeString(Files.createTempFile(null, null), "TEST\n").toFile());

        // Deploy version
        TaskVersion taskVersion2 = request(TaskVersion.class, Given.builder()
                .method(HttpMethod.PATCH)
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/deploy/" + uploadCodeResponse2.getSessionId())
                .body(new DeployRequest()
                        .codeUrl(uploadCodeResponse2.getCodeUrl())
                        .handler("io.dataspray.Runner")
                        .inputQueueNames(List.of("queue2"))
                        .runtime(DeployRequest.RuntimeEnum.NODEJS20_X)
                        .switchToNow(true))
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertNotNull(taskVersion2);
        assertEquals(taskId, taskVersion2.getTaskId());
        assertEquals("2", taskVersion2.getVersion());

        // Check status
        status = request(DeployVersionCheckResponse.class, Given.builder()
                .method(HttpMethod.GET)
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/deploy/" + uploadCodeResponse2.getSessionId()).build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(StatusEnum.SUCCESS, status.getStatus());
        taskVersion2 = status.getResult();
        assertNotNull(taskVersion2);
        assertEquals(taskId, taskVersion2.getTaskId());
        assertEquals("2", taskVersion2.getVersion());

        // Status all
        TaskStatuses taskStatuses = request(TaskStatuses.class, Given.builder()
                .method(HttpMethod.GET)
                .path("/v1/organization/" + getOrganizationName() + "/control/status")
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
                .path("/v1/organization/" + getOrganizationName() + "/control/task/" + taskId + "/delete")
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
