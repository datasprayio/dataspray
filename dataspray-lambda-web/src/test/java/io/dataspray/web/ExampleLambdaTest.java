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
    private enum TestType {
        CUSTOMER_ID(Given.builder()
                .path("/api/customer-id")
                .accountId("1234")
                .build(),
                "1234"),
        CUSTOMER_API_KEY(Given.builder()
                .path("/api/customer-api-key")
                .apiKeyValue("FA2A7539-CC86-4DB7-9718-DA40A3928CAE")
                .build(),
                "FA2A7539-CC86-4DB7-9718-DA40A3928CAE");

        private final Given given;
        private final String expectedBody;
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    public void test(TestType testType) {
        assertEquals(
                testType.getExpectedBody(),
                request(String.class, testType.getGiven())
                        .assertStatusCode(Response.Status.OK.getStatusCode())
                        .getBody());
    }
}
