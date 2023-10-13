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

import com.amazonaws.arn.Arn;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import io.dataspray.authorizer.model.AuthPolicy;
import io.dataspray.authorizer.model.Effect;
import io.dataspray.authorizer.model.HttpMethod;
import io.dataspray.authorizer.model.PolicyDocument;
import io.dataspray.authorizer.model.Statement;
import io.dataspray.common.authorizer.AuthorizerConstants;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Named("authorizer")
public class Authorizer implements RequestHandler<APIGatewayCustomAuthorizerEvent, Object> {
    public static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    public static final Predicate<String> API_KEY_PREDICATE = Pattern.compile("(^\\w*x[\\w-_]?)api[\\w-_]?key\\w*$", Pattern.CASE_INSENSITIVE).asMatchPredicate();

    @Inject
    Gson gson;
    @Inject
    ApiAccessStore apiAccessStore;

    /**
     * This method satisfies "RequestHandler<APIGatewayCustomAuthorizerEvent, Object>" and is used by the above
     * handleRequest.
     * <br />
     * having this method also be called handleRequest confuses Quarkus into thinking this is a StreamHandler
     * causing a ClassCastException.
     */
    public Object handleRequest(APIGatewayCustomAuthorizerEvent event, Context context) {
        try {
            // if the token is valid, a policy should be generated which will allow or deny access to the client

            // if access is denied, the client will receive a 403 Access Denied response
            // if access is allowed, API Gateway will proceed with the back-end integration configured on the method that was called

            // Keep in mind, the policy is cached for 5 minutes by default (TTL is configurable in the authorizer)
            // and will apply to subsequent calls to any method/resource in the RestApi made with the same token

            String apiKeyStr = extractApiKeyFromAuthorization(event);

            ApiAccess apiAccess = apiAccessStore.getApiAccessByApiKey(apiKeyStr, true)
                    .orElseThrow(() -> new ApiGatewayUnauthorized("API access not found"));
            String principalId = apiAccess.getAccountId();

            // Extract endpoint info
            Arn methodArn = Arn.fromString(event.getMethodArn());
            String region = methodArn.getRegion();
            String awsAccountId = methodArn.getAccountId(); // OR event.getRequestContext().getAccountId()
            String restApiId = event.getRequestContext().getApiId();
            String stage = event.getRequestContext().getStage();

            // Send back allow policy
            log.info("Client authorized for account id {}", apiAccess.getAccountId());
            PolicyDocument policyDocument = generatePolicyDocument(region, awsAccountId, restApiId, stage, apiAccess);
            return new AuthPolicy(
                    principalId,
                    policyDocument,
                    apiAccess.getUsageKey(),
                    Map.of(AuthorizerConstants.CONTEXT_KEY_ACCOUNT_ID, apiAccess.getAccountId(),
                            AuthorizerConstants.CONTEXT_KEY_APIKEY_VALUE, apiAccess.getApiKey()));
        } catch (ApiGatewayUnauthorized ex) {
            log.info("Client unauthorized: {}", ex.getReason());
            return ex;
        }
    }

    private String extractApiKeyFromAuthorization(APIGatewayCustomAuthorizerEvent event) throws ApiGatewayUnauthorized {
        String authorizationHeaderValue = event.getHeaders().getOrDefault(AUTHORIZATION_HEADER, "");
        if (authorizationHeaderValue.length() <= 7) {
            throw new ApiGatewayUnauthorized("Authorization header missing or too short");
        }
        if (!authorizationHeaderValue.toLowerCase().startsWith("bearer ")) {
            throw new ApiGatewayUnauthorized("Client unauthorized: Authorization not bearer");
        }
        return authorizationHeaderValue.substring(7);
    }

    @VisibleForTesting
    public static PolicyDocument generatePolicyDocument(String region, String awsAccountId, String restApiId, String stage, ApiAccess apiKey) {

        PolicyDocument policyDocument = new PolicyDocument();

        // Allow all by default
        policyDocument.addStatement(new Statement()
                .setEffect(Effect.ALLOW)
                .setAction("execute-api:Invoke")
                .addResource(Statement.getExecuteApiArn(region, awsAccountId, restApiId, stage,
                        HttpMethod.ALL, Optional.of(getResourcePathAll()))));

        // For Ingest API, for paths ".../account/{accountId}/target/{targetId}/...", only allow your own account id
        // and whitelisted targets.
        // The idea here is to allow all by default and only deny other accounts (except own account). This can also be
        // accomplished by allowing all except accounts and then allowing own account, having others implicitly denied.
        // To verify this works as expected, follow the guide and test against the policy simulator:
        // dataspray-authorizer/src/test/resources/io/dataspray/authorizer/AuthorizerEndpointBase/iam-policy-ingest-path-condition.jsonc
        String accountIdSanitized = sanitizeArnInjection(apiKey.getAccountId());
        Statement accountAndTargetStatement = new Statement()
                .setEffect(Effect.DENY)
                .setAction("execute-api:Invoke")
                .addResources(getResourcePathsAllAccounts()
                        .map(resourcePath ->
                                Statement.getExecuteApiArn(region, awsAccountId, restApiId, stage,
                                        HttpMethod.ALL, Optional.of(resourcePath)))
                        .collect(ImmutableSet.toImmutableSet()));
        final ImmutableSet<String> arnMatchers;
        if (apiKey.getQueueWhitelist().isEmpty()) {
            // Allow all paths under account
            arnMatchers = getResourcePathsForAccount(accountIdSanitized)
                    .map(resourcePath -> Statement.getExecuteApiArn(region, awsAccountId, restApiId, stage,
                            HttpMethod.ALL, Optional.of(resourcePath)))
                    .collect(ImmutableSet.toImmutableSet());
        } else {
            // Allow only paths under own account under whitelisted target queues
            // If you need to add a non-target path, this is where you would add an exception
            arnMatchers = apiKey.getQueueWhitelist().stream()
                    // Sanitize to prevent injection
                    .map(Authorizer::sanitizeArnInjection)
                    // Get resource paths specific to the queue target, not for all of account
                    .flatMap(queue -> getResourcePathsForAccountAndTarget(accountIdSanitized, queue))
                    .map(resourcePath -> Statement.getExecuteApiArn(region, awsAccountId, restApiId, stage,
                            HttpMethod.ALL, Optional.of(resourcePath)))
                    .collect(ImmutableSet.toImmutableSet());
        }
        accountAndTargetStatement.addCondition("StringNotLike", "aws:PrincipalArn", arnMatchers);
        policyDocument.addStatement(accountAndTargetStatement);

        return policyDocument;
    }

    private static String getResourcePathAll() {
        return "*";
    }

    private static Stream<String> getResourcePathsAllAccounts() {
        return Stream.of(
                "/account",
                "/account/*"
        );
    }

    private static Stream<String> getResourcePathsForAccount(String accountIdSanitized) {
        return Stream.of(
                "/account/" + accountIdSanitized,
                "/account/" + accountIdSanitized + "/*"
        );
    }

    private static Stream<String> getResourcePathsForAccountAndTarget(String accountIdSanitized, String target) {
        return Stream.of(
                "/account/" + accountIdSanitized + "/target/" + target,
                "/account/" + accountIdSanitized + "/target/" + target + "/*"
        );
    }

    public static String sanitizeArnInjection(String accountId) {
        return accountId.replaceAll("[^A-Za-z0-9-_]", "");
    }
}
