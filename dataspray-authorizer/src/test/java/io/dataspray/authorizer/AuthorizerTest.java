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

import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import io.dataspray.authorizer.model.AuthPolicy;
import io.dataspray.common.authorizer.AuthorizerConstants;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@QuarkusTest
class AuthorizerTest {

    @Inject
    Authorizer authorizer;
    @Inject
    Gson gson;
    @Inject
    InMemoryApiAccessStore apiAccessStore;

    @Test
    void handleRequest() {
        ApiAccess apiAccess = apiAccessStore.createApiAccess(
                UUID.randomUUID().toString(),
                ApiAccessStore.UsageKeyType.ACCOUNT_WIDE,
                "Description",
                Optional.of(ImmutableSet.of("q1", "q2")),
                Optional.of(Instant.now().plus(Duration.ofDays(3))));
        APIGatewayCustomAuthorizerEvent event = new APIGatewayCustomAuthorizerEvent();
        event.setMethodArn("arn:aws:execute-api:us-east-1:123456789012:abcdef123/default/$connect");
        event.setHeaders(ImmutableMap.of(HttpHeaders.AUTHORIZATION, "bearer " + apiAccess.getApiKey()));
        event.setRequestContext(APIGatewayCustomAuthorizerEvent.RequestContext.builder()
                .withAccountId(apiAccess.getAccountId())
                .withApiId("api-id")
                .withStage("stage")
                .build());

        AuthPolicy authPolicy = gson.fromJson(authorizer.handleRequest(event, null), AuthPolicy.class);
        log.info("Returned AuthPolicy {}", authPolicy);

        assertEquals(apiAccess.getAccountId(), authPolicy.getUsageIdentifierKey());
        assertTrue(authPolicy.getContext().containsKey("apiKey"));
        assertEquals(apiAccess.getAccountId(), authPolicy.getContext().get(AuthorizerConstants.CONTEXT_KEY_ACCOUNT_ID));
        assertEquals(apiAccess.getApiKey(), authPolicy.getContext().get(AuthorizerConstants.CONTEXT_KEY_APIKEY_VALUE));
        assertEquals(apiAccess.getAccountId(), authPolicy.getPrincipalId());
    }
}