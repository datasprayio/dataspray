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

package io.dataspray.authorizer;

import com.google.common.collect.ImmutableSet;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@Slf4j
abstract class AuthorizerEndpointBase {

    @Test
    void testUnauthorized() {
        var rs = given()
                .contentType("application/json")
                .accept("application/json")
                .body(AuthorizerTest.createEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .when()
                .post()
                .then()
                .statusCode(200)
                .body(containsString("Unauthorized"));
    }

    @Test
    void testAuthorized() {
        ApiAccess apiAccess = createApiAccess(
                UUID.randomUUID().toString(),
                ApiAccessStore.UsageKeyType.UNLIMITED,
                "test key",
                Optional.empty(),
                Optional.empty());

        var rs = given()
                .contentType("application/json")
                .accept("application/json")
                .body(AuthorizerTest.createEvent(apiAccess.getAccountId(), apiAccess.getApiKey()))
                .when()
                .post()
                .then()
                .statusCode(200)
                .body(containsString("{\n  \"principalId\": \""
                                     + apiAccess.getAccountId()
                                     + "\",\n  \"context\": {\n    \"apiKey\": \""
                                     + apiAccess.getApiKey()
                                     + "\",\n    \"accountId\": \""
                                     + apiAccess.getAccountId()
                                     + "\"\n  }\n}"));
    }

    /**
     * Since in tests we can inject our in-memory store but in native image integration tests we need to put item into
     * Dynamo, let the concrete tests handle the differences.
     */
    abstract ApiAccess createApiAccess(
            String accountId,
            ApiAccessStore.UsageKeyType usageKeyType,
            String description,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt);
}