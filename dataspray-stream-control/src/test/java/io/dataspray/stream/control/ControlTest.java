package io.dataspray.stream.control;

import io.dataspray.common.aws.test.AwsTestProfile;
import io.dataspray.stream.control.model.TaskStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response.Status;

@QuarkusTest
@TestProfile(AwsTestProfile.class)
public class ControlTest {

    @Test
    public void testStatus() {
        RestAssured.when().get("/v1/control/task/{taskId}/status/",
                        "taskid")
                .then().statusCode(Status.OK.getStatusCode())
                .body(Matchers.equalToObject(TaskStatus.builder()
                        .taskId("taskid")
                        .status(TaskStatus.StatusEnum.MISSING)
                        .build()));
    }

    @Test
    public void testPing() {
        RestAssured.when().get("/v1/ping")
                .then().statusCode(Status.NO_CONTENT.getStatusCode());
    }
}
