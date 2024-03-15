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
import io.dataspray.store.CognitoJwtVerifier;
import io.dataspray.store.UserStore;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.arns.Arn;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.dataspray.store.CognitoJwtVerifier.VerifiedCognitoJwt;

@Slf4j
@Named("authorizer")
public class Authorizer implements RequestHandler<APIGatewayCustomAuthorizerEvent, Object> {
    public static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    public static final Predicate<String> API_KEY_PREDICATE = Pattern.compile("(^\\w*x[\\w-_]?)api[\\w-_]?key\\w*$", Pattern.CASE_INSENSITIVE).asMatchPredicate();

    @Inject
    Gson gson;
    @Inject
    ApiAccessStore apiAccessStore;
    @Inject
    UserStore userStore;
    @Inject
    CognitoJwtVerifier cognitoJwtVerifier;

    @Override
    public Object handleRequest(APIGatewayCustomAuthorizerEvent event, Context context) {
        try {
            // if the token is valid, a policy should be generated which will allow or deny access to the client

            // if access is denied, the client will receive a 403 Access Denied response
            // if access is allowed, API Gateway will proceed with the back-end integration configured on the method that was called

            // Keep in mind, the policy is cached for 5 minutes by default (TTL is configurable in the authorizer)
            // and will apply to subsequent calls to any method/resource in the RestApi made with the same token

            // Extract Authorization header
            String authorizationValue = event.getHeaders()
                    .getOrDefault(AUTHORIZATION_HEADER, "")
                    .trim();
            String authorizationValueLower = authorizationValue
                    .toLowerCase();

            final String identifier;
            final String username;
            final ImmutableSet<String> organizationNames;
            final ImmutableSet<String> queueWhitelist;
            final Optional<String> usageKey;
            if (authorizationValueLower.startsWith("cognito ")) {

                // Parse authorization as Cognito JWT Access Token
                String accessToken = authorizationValue.substring(8);
                VerifiedCognitoJwt verifiedCognitoJwt = cognitoJwtVerifier.verify(accessToken)
                        .orElseThrow(() -> new ApiGatewayUnauthorized("Cognito JWT verification failed"));

                // Extract access info
                username = verifiedCognitoJwt.getUsername();
                organizationNames = verifiedCognitoJwt.getGroupNames();
                queueWhitelist = ImmutableSet.of();
                usageKey = apiAccessStore.getUsageKey(verifiedCognitoJwt.getUsageKeyType(), Optional.of(verifiedCognitoJwt.getUsername()), organizationNames);
                identifier = "user " + verifiedCognitoJwt.getUsername() + " via cognito JWT";

            } else if (authorizationValueLower.startsWith("apikey ")) {

                // Parse authorization as our API Key
                String apiKeyStr = authorizationValue.substring(7);
                ApiAccess apiAccess = apiAccessStore.getApiAccessByApiKey(apiKeyStr, true)
                        .orElseThrow(() -> new ApiGatewayUnauthorized("invalid apikey found"));

                // Extract access info
                username = apiAccess.getOwnerUsername();
                organizationNames = ImmutableSet.of(apiAccess.getOrganizationName());
                queueWhitelist = apiAccess.getQueueWhitelist();
                usageKey = apiAccessStore.getUsageKey(apiAccess.getUsageKeyType(), Optional.of(apiAccess.getOwnerUsername()), ImmutableSet.of(apiAccess.getOrganizationName()));
                switch (apiAccess.getOwnerType()) {
                    case USER -> identifier = "user " + apiAccess.getOwnerUsername() + " via apikey";
                    case TASK ->
                            identifier = "task " + apiAccess.getOwnerTaskId() + " version " + apiAccess.getOwnerTaskVersion() + " via apikey";
                    default ->
                            identifier = apiAccess.getOwnerType() + " owner email " + apiAccess.getOwnerUsername() + " via apikey";
                }

            } else {
                throw new ApiGatewayUnauthorized("Client unauthorized: No valid authorization scheme found");
            }

            // Extract endpoint info
            Arn methodArn = Arn.fromString(event.getMethodArn());
            String region = methodArn.region().orElseThrow();
            String awsAccountId = methodArn.accountId()
                    .orElseGet(() -> event.getRequestContext().getAccountId());
            String restApiId = event.getRequestContext().getApiId();
            String stage = event.getRequestContext().getStage();

            // Send back allow policy
            log.info("Client authorized for {}", identifier);
            PolicyDocument policyDocument = generatePolicyDocument(region, awsAccountId, restApiId, stage, organizationNames, queueWhitelist);
            return new AuthPolicy(
                    username,
                    policyDocument,
                    usageKey,
                    Map.of(
                            AuthorizerConstants.CONTEXT_KEY_USERNAME, username,
                            AuthorizerConstants.CONTEXT_KEY_ORGANIZATION_NAMES, String.join(",", organizationNames)
                    ));
        } catch (ApiGatewayUnauthorized ex) {
            log.info("Client unauthorized: {}", ex.getReason());
            throw ex;
        }
    }

    @VisibleForTesting
    public static PolicyDocument generatePolicyDocument(
            String region,
            String awsAccountId,
            String restApiId,
            String stage,
            ImmutableSet<String> organizationNames,
            ImmutableSet<String> queueWhitelist) {

        PolicyDocument policyDocument = new PolicyDocument();

        // Allow all by default
        policyDocument.addStatement(new Statement()
                .setEffect(Effect.ALLOW)
                .setAction("execute-api:Invoke")
                .addResource(Statement.getExecuteApiArn(region, awsAccountId, restApiId, stage,
                        HttpMethod.ALL, Optional.of(getResourcePathAll()))));

        // For Ingest API, for paths ".../organization/{organizationName}/target/{targetId}/...", only allow your own account id
        // and whitelisted targets.
        // The idea here is to allow all by default and only deny other accounts (except own account). This can also be
        // accomplished by allowing all except accounts and then allowing own account, having others implicitly denied.
        // To verify this works as expected, follow the guide and test against the policy simulator:
        // dataspray-authorizer/src/test/resources/io/dataspray/authorizer/AuthorizerBase/iam-policy-ingest-path-condition.jsonc
        Statement accountAndTargetStatement = new Statement()
                .setEffect(Effect.DENY)
                .setAction("execute-api:Invoke")
                .addResources(getResourcePathsAllAccounts()
                        .map(resourcePath ->
                                Statement.getExecuteApiArn(region, awsAccountId, restApiId, stage,
                                        HttpMethod.ALL, Optional.of(resourcePath)))
                        .collect(ImmutableSet.toImmutableSet()));

        final ImmutableSet.Builder<String> arnMatchersBuilder = ImmutableSet.builder();
        for (String organizationName : organizationNames) {
            String organizationNameSanitized = sanitizeArnInjection(organizationName);
            if (queueWhitelist.isEmpty()) {
                // Allow all paths under account
                arnMatchersBuilder.addAll(getResourcePathsForOrganization(organizationNameSanitized)
                        .map(resourcePath -> Statement.getExecuteApiArn(region, awsAccountId, restApiId, stage,
                                HttpMethod.ALL, Optional.of(resourcePath)))
                        .collect(ImmutableSet.toImmutableSet()));
            } else {
                // Allow only paths under own account under whitelisted target queues
                // If you need to add a non-target path, this is where you would add an exception
                arnMatchersBuilder.addAll(queueWhitelist.stream()
                        // Sanitize to prevent injection
                        .map(Authorizer::sanitizeArnInjection)
                        // Get resource paths specific to the queue target, not for all of account
                        .flatMap(queue -> getResourcePathsForAccountAndTarget(organizationNameSanitized, queue))
                        .map(resourcePath -> Statement.getExecuteApiArn(region, awsAccountId, restApiId, stage,
                                HttpMethod.ALL, Optional.of(resourcePath)))
                        .collect(ImmutableSet.toImmutableSet()));
            }
        }
        accountAndTargetStatement.addCondition("StringNotLike", "aws:PrincipalArn", arnMatchersBuilder.build());
        policyDocument.addStatement(accountAndTargetStatement);

        return policyDocument;
    }

    private static String getResourcePathAll() {
        return "*";
    }

    private static Stream<String> getResourcePathsAllAccounts() {
        return Stream.of(
                "/organization",
                "/organization/*"
        );
    }

    private static Stream<String> getResourcePathsForOrganization(String accountIdSanitized) {
        return Stream.of(
                "/organization/" + accountIdSanitized,
                "/organization/" + accountIdSanitized + "/*"
        );
    }

    private static Stream<String> getResourcePathsForAccountAndTarget(String accountIdSanitized, String target) {
        return Stream.of(
                "/organization/" + accountIdSanitized + "/target/" + target,
                "/organization/" + accountIdSanitized + "/target/" + target + "/*"
        );
    }

    public static String sanitizeArnInjection(String accountId) {
        return accountId.replaceAll("[^A-Za-z0-9-_]", "");
    }
}
