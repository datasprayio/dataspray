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
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import io.dataspray.authorizer.model.AuthPolicy;
import io.dataspray.authorizer.model.AuthPolicy.HttpMethod;
import io.dataspray.authorizer.model.AuthPolicy.PolicyDocument;
import io.dataspray.common.authorizer.AuthorizerConstants;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public class Authorizer implements RequestHandler<APIGatewayCustomAuthorizerEvent, String> {
    public static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    public static final Predicate<String> API_KEY_PREDICATE = Pattern.compile("(^\\w*x[\\w-_]?)api[\\w-_]?key\\w*$", Pattern.CASE_INSENSITIVE).asMatchPredicate();

    @Inject
    Gson gson;
    @Inject
    ApiAccessStore apiAccessStore;

    @Override
    public String handleRequest(APIGatewayCustomAuthorizerEvent event, Context context) {
        try {
            String apiKeyStr = extractApiKeyFromAuthorization(event);

            ApiAccess apiAccess = apiAccessStore.getApiAccessByApiKey(apiKeyStr, true)
                    .orElseThrow(() -> new NotAuthorizedException("Bearer"));
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
            return gson.toJson(new AuthPolicy(
                    principalId,
                    policyDocument,
                    apiAccess.getUsageKey(),
                    Map.of(AuthorizerConstants.CONTEXT_KEY_ACCOUNT_ID, apiAccess.getAccountId(),
                            AuthorizerConstants.CONTEXT_KEY_APIKEY_VALUE, apiAccess.getApiKey())));
        } catch (NotAuthorizedException ex) {
            log.info("Client unauthorized {}", ex.getChallenges());
            // if the client token is not recognized or invalid, API Gateway
            // accepts a 401 Unauthorized response to the client by failing like so.
            // https://github.com/awslabs/aws-apigateway-lambda-authorizer-blueprints/blob/master/blueprints/java/src/example/APIGatewayAuthorizerHandler.java#L42
            // throw new RuntimeException("Unauthorized");
            // However, it also accepts a return string of "Unauthorized" which is a bit more elegant
            // https://stackoverflow.com/questions/64909861/how-to-return-401-from-aws-lambda-authorizer-without-raising-an-exception#comment132887021_66788544
            return "Unauthorized";
        }
    }

    private String extractApiKeyFromAuthorization(APIGatewayCustomAuthorizerEvent event) throws NotAuthorizedException {
        String authorizationHeaderValue = event.getHeaders().getOrDefault(AUTHORIZATION_HEADER, "");
        if (authorizationHeaderValue.length() <= 7) {
            throw new NotAuthorizedException("Bearer");
        }
        if (!authorizationHeaderValue.toLowerCase().startsWith("bearer ")) {
            throw new NotAuthorizedException("Bearer");
        }
        return authorizationHeaderValue.substring(7);
    }

    private PolicyDocument generatePolicyDocument(String region, String awsAccountId, String restApiId, String stage, ApiAccess apiKey) {

        PolicyDocument policyDocument = new PolicyDocument(region, awsAccountId, restApiId, stage);

        // if the token is valid, a policy should be generated which will allow or deny access to the client

        // if access is denied, the client will receive a 403 Access Denied response
        // if access is allowed, API Gateway will proceed with the back-end integration configured on the method that was called

        // Keep in mind, the policy is cached for 5 minutes by default (TTL is configurable in the authorizer)
        // and will apply to subsequent calls to any method/resource in the RestApi made with the same token

        // Allow all by default
        policyDocument.createStatement(AuthPolicy.Effect.ALLOW, HttpMethod.ALL, "*");

        // For Ingest API, for paths ".../account/{accountId}/target/{targetId}/...", only allow your own account id
        // and whitelisted targets.
        // The idea here is to allow all by default and only deny other accounts (except own account). This can also be
        // accomplished by allowing all except accounts and then allowing own account, having others implicitly denied.
        AuthPolicy.Statement accountAndTargetStatement = policyDocument.createStatement(AuthPolicy.Effect.DENY, HttpMethod.ALL, "*");
        String accountId = apiKey.getAccountId()
                // Sanitize to prevent injection
                .replaceAll("[^A-Za-z0-9]", "");
        final ImmutableSet<String> arnMatchers;
        if (apiKey.getQueueWhitelist().isEmpty()) {
            // Allow all paths under account
            arnMatchers = ImmutableSet.of(
                    "arn:aws:execute-api:*:*:*/account/" + accountId + "/*",
                    "arn:aws:execute-api:*:*:*/account/" + accountId
            );
        } else {
            // Allow only paths under own account under whitelisted targets
            // To add a non-target path, need to add an exception here
            arnMatchers = apiKey.getQueueWhitelist().stream()
                    // Sanitize to prevent injection
                    .map(queue -> queue.replaceAll("[^A-Za-z0-9]", ""))
                    .flatMap(queue -> Stream.of(
                            "arn:aws:execute-api:*:*:*/account/" + accountId + "/target/" + queue + "/*",
                            "arn:aws:execute-api:*:*:*/account/" + accountId + "/target/" + queue
                    ))
                    .collect(ImmutableSet.toImmutableSet());
        }
        accountAndTargetStatement.addCondition("StringNotLike", "aws:PrincipalArn", arnMatchers);

        return policyDocument;
    }
}
