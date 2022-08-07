package io.dataspray.lambda;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class ExampleTest {

    @Test
    public void testPing() {
        RestAssured.when()
                .get("/api/ping")
                .then()
                .body(equalTo("OK"));
    }
}
