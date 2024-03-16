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

package io.dataspray.authorizer;

import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.common.authorizer.AuthorizerConstants;
import io.dataspray.common.test.AbstractTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static io.dataspray.common.test.JsonMatcher.jsonStringEqualTo;
import static io.dataspray.store.impl.DynamoApiGatewayApiAccessStore.API_KEY_LENGTH;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@QuarkusTestResource(MotoLifecycleManager.class)
abstract class AuthorizerBase extends AbstractTest {

    private enum TestType {
        UNAUTHORIZED_NONE,
        UNAUTHORIZED_EXPIRED,
        AUTHORIZED_UNLIMITED,
        AUTHORIZED_LIMITED_ORGANIZATION,
        AUTHORIZED_LIMITED_GLOBAL,
        AUTHORIZED_QUEUE_WHITELIST,
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    void test(TestType testType) throws Exception {
        // Given test setup
        String requestAccountId = UUID.randomUUID().toString();
        Optional<ApiAccess> apiAccessOpt = Optional.empty();
        switch (testType) {
            case UNAUTHORIZED_EXPIRED:
            case AUTHORIZED_UNLIMITED:
            case AUTHORIZED_LIMITED_ORGANIZATION:
            case AUTHORIZED_LIMITED_GLOBAL:
            case AUTHORIZED_QUEUE_WHITELIST:
                apiAccessOpt = Optional.of(createApiAccess(
                        "fd376965-10d2-43b3-a16c-35d3d8f0455a",
                        switch (testType) {
                            case AUTHORIZED_LIMITED_ORGANIZATION -> ApiAccessStore.UsageKeyType.ORGANIZATION;
                            case AUTHORIZED_LIMITED_GLOBAL -> ApiAccessStore.UsageKeyType.GLOBAL;
                            default -> ApiAccessStore.UsageKeyType.UNLIMITED;
                        },
                        testType == TestType.AUTHORIZED_QUEUE_WHITELIST
                                ? Optional.of(ImmutableSet.of("whitelisted-queue-1", "whitelisted-queue-2"))
                                : Optional.empty(),
                        testType == TestType.UNAUTHORIZED_EXPIRED
                                ? Optional.of(Instant.now().minusSeconds(10))
                                : Optional.empty()));
                break;
        }

        // Run this twice to test caching
        for (int i = 0; i < 2; i++) {
            log.info("Testing {}", i == 0 ? "cache miss" : "cache hit");

            // When we call the endpoint
            var response = given()
                    .contentType("application/json")
                    .accept("application/json")
                    .body(createEvent(
                            apiAccessOpt.map(ApiAccess::getApiKey).orElse(UUID.randomUUID().toString())))
                    .when()
                    .post()
                    .then()
                    .log().body();

            // Then assert response
            switch (testType) {
                case UNAUTHORIZED_NONE:
                case UNAUTHORIZED_EXPIRED:
                    response.statusCode(500)
                            .body(equalTo("{\"errorType\":\"io.dataspray.authorizer.ApiGatewayUnauthorized\",\"errorMessage\":\"Unauthorized\"}"));
                    break;
                case AUTHORIZED_UNLIMITED:
                case AUTHORIZED_LIMITED_ORGANIZATION:
                case AUTHORIZED_LIMITED_GLOBAL:
                case AUTHORIZED_QUEUE_WHITELIST:
                    ApiAccess apiAccess = apiAccessOpt.get();
                    response.statusCode(200)
                            .body("principalId", equalTo(apiAccess.getPrincipalId()))
                            .body("usageIdentifierKey", equalTo(switch (apiAccess.getUsageKeyType()) {
                                case ORGANIZATION -> "dataspray-usage-key-1-" + apiAccess.getOrganizationName();
                                case GLOBAL -> "dataspray-usage-key-2-GLOBAL";
                                case UNLIMITED -> null;
                            }))
                            .body("context." + AuthorizerConstants.CONTEXT_KEY_USERNAME, equalTo(apiAccess.getOwnerUsername()))
                            .body("context." + AuthorizerConstants.CONTEXT_KEY_ORGANIZATION_NAMES, equalTo(apiAccess.getOrganizationName()))
                            .body("policyDocument", jsonStringEqualTo(new String(getTestResourceBytes(
                                    testType == TestType.AUTHORIZED_QUEUE_WHITELIST
                                            ? "authorized-queue-whitelist.json"
                                            : "authorized.json",
                                    AuthorizerBase.class))));
                    break;
                default:
                    fail();
            }
        }
    }

    private ApiAccess createApiAccess(
            String organizationName,
            ApiAccessStore.UsageKeyType usageKeyType,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt) {

        ApiAccess apiAccess = new ApiAccess(
                getKeygenUtil().generateSecureApiKey(API_KEY_LENGTH),
                organizationName,
                ApiAccessStore.OwnerType.USER,
                "user@example.com",
                null,
                null,
                usageKeyType,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null));

        TableSchema<ApiAccess> apiAccessSchema = getSingleTable().parseTableSchema(ApiAccess.class);
        getDynamo().putItem(PutItemRequest.builder()
                .tableName(apiAccessSchema.tableName())
                .item(apiAccessSchema.toAttrMap(apiAccess)).build());

        return apiAccess;

    }

    protected abstract SingleTable getSingleTable();

    protected abstract DynamoDbClient getDynamo();

    protected abstract KeygenUtil getKeygenUtil();

    static APIGatewayCustomAuthorizerEvent createEvent(String apiKey) {
        APIGatewayCustomAuthorizerEvent event = new APIGatewayCustomAuthorizerEvent();
        event.setMethodArn("arn:aws:execute-api:us-east-1:123456789012:abcdef123/default/$connect");
        event.setHeaders(ImmutableMap.of(HttpHeaders.AUTHORIZATION.toLowerCase(), "apikey " + apiKey));
        event.setRequestContext(APIGatewayCustomAuthorizerEvent.RequestContext.builder()
                .withAccountId("100000000001") // AWS account id
                .withApiId("api-id")
                .withStage("stage")
                .build());
        return event;
    }
}