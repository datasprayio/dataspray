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

package io.dataspray.stream.control;

import com.google.common.collect.ImmutableList;
import io.dataspray.store.LambdaDeployerImpl;
import io.dataspray.store.SqsQueueStore;
import io.dataspray.web.BaseLambdaWebServiceStack;
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
public class ControlStack extends BaseLambdaWebServiceStack {

    @Getter
    private final ManagedPolicy customerFunctionPermissionBoundaryManagedPolicy;
    @Getter
    private final Bucket bucketCode;

    public ControlStack(Construct parent, String env, String codeDir) {
        super(parent, Options.builder()
                .env(env)
                .functionName("control-" + env)
                .codePath(codeDir + "/control.zip")
                .build());


        customerFunctionPermissionBoundaryManagedPolicy = ManagedPolicy.Builder.create(this, LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME)
                .managedPolicyName(LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME)
                .description("Permission boundary for customer lambdas")
                .statements(ImmutableList.of(PolicyStatement.Builder.create()
                                .sid(LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LOGGING_PREFIX + "PermissionBoundary")
                                .effect(Effect.ALLOW)
                                .actions(ImmutableList.of(
                                        "logs:CreateLogGroup",
                                        "logs:CreateLogStream",
                                        "logs:PutLogEvents"))
                                .resources(ImmutableList.of(
                                        "arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:/aws/lambda/" + LambdaDeployerImpl.FUN_NAME_WILDCARD,
                                        "arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:/aws/lambda/" + LambdaDeployerImpl.FUN_NAME_WILDCARD + ":" + LambdaDeployerImpl.LAMBDA_ACTIVE_QUALIFIER))
                                .build(),
                        PolicyStatement.Builder.create()
                                .sid(LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_SQS + "PermissionBoundary")
                                .effect(Effect.ALLOW)
                                .actions(ImmutableList.of(
                                        "sqs:ReceiveMessage",
                                        "sqs:DeleteMessage",
                                        "sqs:GetQueueAttributes"))
                                .resources(ImmutableList.of(
                                        "arn:aws:sqs:" + getRegion() + ":" + getAccount() + ":" + SqsQueueStore.CUSTOMER_QUEUE_WILDCARD))
                                .build()))
                .build();

        bucketCode = Bucket.Builder.create(this, getSubConstructId("control-code-upload-bucket"))
                .bucketName(LambdaDeployerImpl.CODE_BUCKET_NAME)
                .autoDeleteObjects(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .lifecycleRules(ImmutableList.of(
                        LifecycleRule.builder()
                                .expiration(Duration.days(1)).build()))
                .build();
        getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "s3:PutObject",
                        "s3:GetObject"))
                .resources(ImmutableList.of(bucketCode.getBucketArn() + "/*"))
                .build());

        getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid("CustomerManagementLambda")
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        // CRUD
                        "lambda:GetFunction",
                        "lambda:ListVersionsByFunction",
                        "lambda:CreateFunction",
                        "lambda:DeleteFunction",
                        "lambda:UpdateFunctionCode",
                        "lambda:UpdateFunctionConfiguration",
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
                        "arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + LambdaDeployerImpl.FUN_NAME_WILDCARD,
                        // Event source mappings are referred to by UUID, since there is no way to restrict this,
                        // we use a wildcard here
                        "arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":event-source-mapping:*"))
                .build());
        // Unfortunately not all permissions allow for resource-specific restrictions.
        getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid("CustomerManagementLambdaResourceWildcardActions")
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "lambda:ListFunctions",
                        "lambda:ListEventSourceMappings",
                        "lambda:CreateEventSourceMapping"))
                .resources(ImmutableList.of("*"))
                .build());

        getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid("CustomerManagementSqs")
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "sqs:CreateQueue",
                        "sqs:GetQueueAttributes",
                        "sqs:GetQueueUrl"))
                .resources(ImmutableList.of(
                        "arn:aws:sqs:" + getRegion() + ":" + getAccount() + ":" + SqsQueueStore.CUSTOMER_QUEUE_WILDCARD))
                .build());

        getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid("CustomerManagementIam")
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "iam:GetRole",
                        "iam:GetRolePolicy",
                        "iam:PassRole",
                        "iam:CreateRole",
                        "iam:PutRolePolicy"))
                .resources(ImmutableList.of(
                        "arn:aws:iam::" + getAccount() + ":policy/" + LambdaDeployerImpl.CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "*",
                        "arn:aws:iam::" + getAccount() + ":role/" + LambdaDeployerImpl.CUSTOMER_FUN_AND_ROLE_NAME_PREFIX + "*"))
                .build());
    }
}
