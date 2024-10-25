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

package io.dataspray.web;

import io.dataspray.common.test.aws.AbstractLambdaTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@QuarkusTest
public class ExampleLambdaTest extends AbstractLambdaTest {

    @Getter
    @AllArgsConstructor
    public enum TestType {
        PING(Given.builder()
                .path("/api/ping")
                .build(),
                "OK"),
        USERNAME(Given.builder()
                .path("/api/username")
                .username("expected.username")
                .build(),
                "expected.username");

        private final Given given;
        private final String expectedBody;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TestType.class)
    public void test(TestType testType) {
        assertEquals(
                testType.getExpectedBody(),
                request(String.class, testType.getGiven())
                        .assertStatusCode(Response.Status.OK.getStatusCode())
                        .getBody());
    }
}
