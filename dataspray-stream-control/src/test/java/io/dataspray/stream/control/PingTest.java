package io.dataspray.stream.control;

import io.dataspray.common.aws.test.AwsTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(AwsTestProfile.class)
public class PingTest {

    @Test
    public void test() {
        RestAssured.when().get("/v1/ping")
                .then().statusCode(Status.NO_CONTENT.getStatusCode());
    }
}
