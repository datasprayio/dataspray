package io.dataspray.stream.ingest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response.Status;

@QuarkusTest
public class PingTest {

    @Test
    public void testPing() {
        RestAssured.when().get("/v1/ping")
                .then().statusCode(Status.NO_CONTENT.getStatusCode());
    }
}
