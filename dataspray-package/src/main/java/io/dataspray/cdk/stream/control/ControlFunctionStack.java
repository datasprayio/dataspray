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

package io.dataspray.cdk.stream.control;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.cdk.api.ApiFunctionStack;
import io.dataspray.cdk.site.NextSiteStack;
import io.dataspray.cdk.store.AuthNzStack;
import io.dataspray.cdk.store.SingleTableStack;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.store.impl.LambdaDeployerImpl;
import io.dataspray.store.impl.SqsStreamStore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;

@Slf4j
@Getter
public class ControlFunctionStack extends ApiFunctionStack {

    /** Separated out to remove cyclic dependency */
    private final String customerFunctionPermissionBoundaryManagedPolicyName;
    private final ManagedPolicy customerFunctionPermissionBoundaryManagedPolicy;
    /** Separated out to remove cyclic dependency */
    private final String bucketCodeName;
    private final Bucket bucketCode;

    public ControlFunctionStack(Construct parent, DeployEnvironment deployEnv, String codeZip, AuthNzStack authNzStack, NextSiteStack dashboardSiteStack, SingleTableStack singleTableStack) {
        super(parent, Options.builder()
                .deployEnv(deployEnv)
                .baseFunctionName("control")
                .codeZip(codeZip)
                .apiTags(ImmutableSet.of(
                        "AuthNZ",
                        "Control",
                        "Organization"))
                .corsForSite(dashboardSiteStack)
                .memorySize(256)
                .memorySizeNative(256)
                .build());

        customerFunctionPermissionBoundaryManagedPolicyName = getConstructId("customer-function-permission-boundary");
        customerFunctionPermissionBoundaryManagedPolicy = ManagedPolicy.Builder.create(this, getConstructId("customer-function-permission-boundary"))
                .managedPolicyName(customerFunctionPermissionBoundaryManagedPolicyName)
                .description("Permission boundary for customer lambdas")
                .statements(ImmutableList.of(
                        PolicyStatement.Builder.create()
                                .sid(getConstructIdCamelCase(LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LOGGING_PREFIX + "PermissionBoundary"))
                                .effect(Effect.ALLOW)
                                .actions(ImmutableList.of(
                                        "logs:CreateLogGroup",
                                        "logs:CreateLogStream",
                                        "logs:PutLogEvents"))
                                .resources(ImmutableList.of(
                                        "arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:/aws/lambda/" + LambdaDeployerImpl.FUN_NAME_WILDCARD_GETTER.apply(getDeployEnv()),
                                        "arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:/aws/lambda/" + LambdaDeployerImpl.FUN_NAME_WILDCARD_GETTER.apply(getDeployEnv()) + ":" + LambdaDeployerImpl.LAMBDA_ACTIVE_QUALIFIER))
                                .build(),
                        PolicyStatement.Builder.create()
                                .sid(getConstructIdCamelCase(LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_DYNAMO + "PermissionBoundary"))
                                .effect(Effect.ALLOW)
                                .actions(ImmutableList.of(
                                        "dynamodb:GetItem",
                                        "dynamodb:BatchGetItem",
                                        "dynamodb:Query",
                                        "dynamodb:PutItem",
                                        "dynamodb:UpdateItem",
                                        "dynamodb:BatchWriteItem",
                                        "dynamodb:DeleteItem"))
                                .resources(ImmutableList.of(
                                        "arn:aws:dynamodb:" + getRegion() + ":" + getAccount() + ":table/" + LambdaDeployerImpl.FUN_NAME_WILDCARD_GETTER.apply(getDeployEnv()),
                                        "arn:aws:dynamodb:" + getRegion() + ":" + getAccount() + ":table/" + LambdaDeployerImpl.FUN_NAME_WILDCARD_GETTER.apply(getDeployEnv()) + "/index/*"))
                                .build(),
                        PolicyStatement.Builder.create()
                                .sid(getConstructIdCamelCase(LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_SQS + "PermissionBoundary"))
                                .effect(Effect.ALLOW)
                                .actions(ImmutableList.of(
                                        "sqs:ChangeMessageVisibility",
                                        "sqs:ReceiveMessage",
                                        "sqs:DeleteMessage",
                                        "sqs:GetQueueAttributes"))
                                .resources(ImmutableList.of(
                                        "arn:aws:sqs:" + getRegion() + ":" + getAccount() + ":" + SqsStreamStore.CUSTOMER_QUEUE_WILDCARD))
                                .build()))
                .build();

        bucketCodeName = getConstructId("code-upload-bucket");
        bucketCode = Bucket.Builder.create(this, getConstructId("code-upload-bucket"))
                .bucketName(bucketCodeName)
                .autoDeleteObjects(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .lifecycleRules(ImmutableList.of(
                        LifecycleRule.builder()
                                .expiration(Duration.days(1)).build()))
                .build();
        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("CodeUploadBucket"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "s3:PutObject",
                        "s3:GetObject"))
                .resources(ImmutableList.of(bucketCode.getBucketArn() + "/*"))
                .build());

        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("SingleTable"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "dynamodb:CreateTable",
                        "dynamodb:GetItem",
                        "dynamodb:BatchGetItem",
                        "dynamodb:Query",
                        "dynamodb:PutItem",
                        "dynamodb:UpdateItem",
                        "dynamodb:BatchWriteItem",
                        "dynamodb:DeleteItem"))
                .resources(ImmutableList.of(
                        singleTableStack.getSingleTableTable().getTableArn(),
                        singleTableStack.getSingleTableTable().getTableArn() + "/index/*"))
                .build());
        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("ApiGateway"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "apigateway:POST")) // POST is for creating API Keys and Usage Plan Keys
                .resources(ImmutableList.of(
                        // NOTE: this is broad permission for any API Gateway since adding the API Gateway ARN
                        // would create a circular dependency between the two stacks.
                        "arn:aws:apigateway:" + getRegion() + "::/apikeys",
                        "arn:aws:apigateway:" + getRegion() + "::/usageplans/*/keys"))
                .build());
        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("CustomerManagementLambda"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        // CRUD
                        "lambda:GetFunction",
                        "lambda:ListVersionsByFunction",
                        "lambda:CreateFunction",
                        "lambda:DeleteFunction",
                        "lambda:UpdateFunctionCode",
                        "lambda:GetFunctionConfiguration",
                        "lambda:UpdateFunctionConfiguration",
                        "lambda:PublishVersion",
                        // Function URL
                        "lambda:GetFunctionUrlConfig",
                        "lambda:DeleteFunctionUrlConfig",
                        "lambda:CreateFunctionUrlConfig",
                        "lambda:UpdateFunctionUrlConfig",
                        // Concurrency
                        "lambda:GetFunctionConcurrency",
                        "lambda:PutFunctionConcurrency",
                        // Event sources
                        "lambda:UpdateEventSourceMapping",
                        "lambda:GetEventSourceMapping",
                        // Aliases
                        "lambda:CreateAlias",
                        "lambda:UpdateAlias",
                        "lambda:GetAlias",
                        // Permissions
                        "lambda:AddPermission",
                        "lambda:GetPolicy"))
                .resources(ImmutableList.of(
                        "arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + LambdaDeployerImpl.FUN_NAME_WILDCARD_GETTER.apply(deployEnv),
                        // Event source mappings are referred to by UUID, since there is no way to restrict this,
                        // we use a wildcard here
                        "arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":event-source-mapping:*"))
                .build());
        // Allow management of customer's DynamoDB tables
        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("CustomerManagementDynamoControl"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "dynamodb:CreateTable",
                        "dynamodb:DescribeTable",
                        "dynamodb:UpdateTable",
                        "dynamodb:DescribeTimeToLive",
                        "dynamodb:UpdateTimeToLive"))
                .resources(ImmutableList.of(
                        "arn:aws:dynamodb:" + getRegion() + ":" + getAccount() + ":table/" + LambdaDeployerImpl.CUSTOMER_FUN_DYNAMO_OR_ROLE_NAME_PREFIX_GETTER.apply(getDeployEnv()) + "*"
                ))
                .build());
        // Unfortunately not all permissions allow for resource-specific restrictions.
        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("CustomerManagementLambdaResourceWildcardActions"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "lambda:ListFunctions",
                        "lambda:ListEventSourceMappings",
                        "lambda:CreateEventSourceMapping"))
                .resources(ImmutableList.of("*"))
                .build());

        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("CustomerManagementSqs"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "sqs:CreateQueue",
                        "sqs:ChangeMessageVisibility",
                        "sqs:ReceiveMessage",
                        "sqs:DeleteMessage",
                        "sqs:GetQueueAttributes",
                        "sqs:GetQueueUrl"))
                .resources(ImmutableList.of(
                        "arn:aws:sqs:" + getRegion() + ":" + getAccount() + ":" + SqsStreamStore.CUSTOMER_QUEUE_WILDCARD))
                .build());

        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("CustomerManagementIam"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "iam:GetRole",
                        "iam:GetRolePolicy",
                        "iam:PassRole",
                        "iam:CreateRole",
                        "iam:PutRolePolicy"))
                .resources(ImmutableList.of(
                        "arn:aws:iam::" + getAccount() + ":policy/" + LambdaDeployerImpl.CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "*",
                        "arn:aws:iam::" + getAccount() + ":role/" + LambdaDeployerImpl.CUSTOMER_FUN_DYNAMO_OR_ROLE_NAME_PREFIX_GETTER.apply(getDeployEnv()) + "*"))
                .build());

        getApiFunction().getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("AuthnzCognitoUserPool"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "cognito-idp:AdminInitiateAuth",
                        "cognito-idp:ConfirmSignUp",
                        "cognito-idp:SignUp",
                        "cognito-idp:AdminListGroupsForUser",
                        "cognito-idp:CreateGroup",
                        "cognito-idp:GetGroup",
                        "cognito-idp:ListUsersInGroup",
                        "cognito-idp:UpdateGroup",
                        "cognito-idp:AdminAddUserToGroup",
                        "cognito-idp:AdminRemoveUserFromGroup",
                        "cognito-idp:AdminRespondToAuthChallenge",
                        "cognito-idp:ResendConfirmationCode",
                        "cognito-idp:AdminGetUser",
                        "cognito-idp:AssociateSoftwareToken",
                        "cognito-idp:VerifySoftwareToken"))
                .resources(ImmutableList.of(authNzStack.getUserPool().getUserPoolArn()))
                .build());
    }
}
