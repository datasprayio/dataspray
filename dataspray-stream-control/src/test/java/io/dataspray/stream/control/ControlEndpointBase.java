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

import io.dataspray.common.json.GsonUtil;
import io.dataspray.stream.client.StreamApiImpl;
import io.dataspray.stream.control.client.model.UploadCodeResponse;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.UploadCodeRequest;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.nio.file.Files;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasEntry;

@Slf4j
public abstract class ControlEndpointBase {

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
        io.restassured.response.Response response = given()
                .contentType("application/json")
                .accept("application/json")
                .body(UploadCodeRequest.builder()
                        .taskId(taskId)
                        .contentLengthBytes(12L).build())

                .when()
                .put("/code/upload");

        response.then()
                .statusCode(Response.Status.OK.getStatusCode())
                .log().body()
                .assertThat()
                .body("codeUrl", startsWith("s3://io-dataspray-code-upload/customer/123/task1-"));
        UploadCodeResponse uploadCodeResponse = GsonUtil.get().fromJson(
                response.body().asString(),
                UploadCodeResponse.class);

        // Upload to S3
        new StreamApiImpl().uploadCode(
                uploadCodeResponse.getPresignedUrl(),
                Files.writeString(Files.createTempFile(null, null), "test\n").toFile());

        // Deploy version
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(DeployRequest.builder()
                        .codeUrl(uploadCodeResponse.getCodeUrl())
                        .handler("io.dataspray.Runner")
                        .inputQueueNames(List.of("queue1"))
                        .runtime(DeployRequest.RuntimeEnum.JAVA11)
                        .switchToNow(false).build())

                .when()
                .patch("/control/task/{taskId}/deploy", taskId)

                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .log().body()
                .assertThat()
                .body("taskId", equalTo(taskId))
                .body("version", equalTo("1"));

        // Activate version
        given()
                .contentType("application/json")
                .accept("application/json")
                .queryParam("version", "1")

                .when()
                .patch("/control/task/{taskId}/activate", taskId)

                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .log().body()
                .assertThat()
                .body("taskId", equalTo(taskId))
                .body("version", equalTo("1"))
                .body("status", equalTo(TaskStatus.StatusEnum.RUNNING.name()));

        // Pause
        given()
                .contentType("application/json")
                .accept("application/json")

                .when()
                .patch("/control/task/{taskId}/pause", taskId)

                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .log().body()
                .assertThat()
                .body("taskId", equalTo(taskId))
                .body("version", equalTo("1"))
                .body("status", equalTo(TaskStatus.StatusEnum.PAUSED.name()));

        // Resume
        given()
                .contentType("application/json")
                .accept("application/json")

                .when()
                .patch("/control/task/{taskId}/resume", taskId)

                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .log().body()
                .assertThat()
                .body("taskId", equalTo(taskId))
                .body("version", equalTo("1"))
                .body("status", equalTo(TaskStatus.StatusEnum.RUNNING.name()));

        // Status all
        given()
                .contentType("application/json")
                .accept("application/json")

                .when()
                .get("/control/status")

                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .log().body()
                .assertThat()
                .body("tasks", hasItem(allOf(
                        hasEntry("taskId", taskId),
                        hasEntry("version", "1"),
                        hasEntry("status", TaskStatus.StatusEnum.RUNNING.name())
                )));

        // Delete
        given()
                .contentType("application/json")
                .accept("application/json")

                .when()
                .delete("/control/task/{taskId}/delete", taskId)

                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .log().body()
                .assertThat()
                .body("taskId", equalTo(taskId))
                .body("version", nullValue())
                .body("status", equalTo(TaskStatus.StatusEnum.NOTFOUND.name()));

    }
}
