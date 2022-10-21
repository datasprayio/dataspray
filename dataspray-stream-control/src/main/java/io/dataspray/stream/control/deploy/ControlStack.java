package io.dataspray.stream.control.deploy;

import com.google.common.collect.ImmutableList;
import io.dataspray.lambda.deploy.LambdaBaseStack;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;

import static io.dataspray.store.LambdaDeployerImpl.*;
import static io.dataspray.store.SqsQueueStore.CUSTOMER_QUEUE_WILDCARD;

@Slf4j
public class ControlStack extends LambdaBaseStack {

    private final ManagedPolicy customerFunctionPermissionBoundaryManagedPolicy;
    private final Bucket bucketCode;

    public ControlStack(Construct parent) {
        super(parent, Options.builder()
                .openapiYamlPath("target/openapi/api-control.yaml")
                .build());

        customerFunctionPermissionBoundaryManagedPolicy = ManagedPolicy.Builder.create(this, CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME)
                .managedPolicyName(CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME)
                .description("Permission boundary for customer lambdas")
                .statements(ImmutableList.of(PolicyStatement.Builder.create()
                                .sid(CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LOGGING_PREFIX + "PermissionBoundary")
                                .effect(Effect.ALLOW)
                                .actions(ImmutableList.of(
                                        "logs:CreateLogGroup",
                                        "logs:CreateLogStream",
                                        "logs:PutLogEvents"))
                                .resources(ImmutableList.of(
                                        "arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:/aws/lambda/" + FUN_NAME_WILDCARD,
                                        "arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:/aws/lambda/" + FUN_NAME_WILDCARD + ":" + LAMBDA_ACTIVE_QUALIFIER))
                                .build(),
                        PolicyStatement.Builder.create()
                                .sid(CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_SQS + "PermissionBoundary")
                                .effect(Effect.ALLOW)
                                .actions(ImmutableList.of(
                                        "sqs:ReceiveMessage",
                                        "sqs:DeleteMessage",
                                        "sqs:GetQueueAttributes"))
                                .resources(ImmutableList.of(
                                        "arn:aws:sqs:" + getRegion() + ":" + getAccount() + ":" + CUSTOMER_QUEUE_WILDCARD + ":" + LAMBDA_ACTIVE_QUALIFIER))
                                .build()))
                .build();

        bucketCode = Bucket.Builder.create(this, "control-code-upload-bucket")
                .bucketName(CODE_BUCKET_NAME)
                .autoDeleteObjects(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .lifecycleRules(ImmutableList.of(
                        LifecycleRule.builder()
                                .expiration(Duration.days(1)).build()))
                .build();
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "s3:PutObject",
                        "s3:GetObject"))
                .resources(ImmutableList.of(bucketCode.getBucketArn() + "/*"))
                .build());

        function.addToRolePolicy(PolicyStatement.Builder.create()
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
                        "arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + FUN_NAME_WILDCARD,
                        // Event source mappings are referred to by UUID, since there is no way to restrict this,
                        // we use a wildcard here
                        "arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":event-source-mapping:*"))
                .build());
        // Unfortunately not all permissions allow for resource-specific restrictions.
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .sid("CustomerManagementLambdaResourceWildcardActions")
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "lambda:ListFunctions",
                        "lambda:ListEventSourceMappings",
                        "lambda:CreateEventSourceMapping"))
                .resources(ImmutableList.of("*"))
                .build());

        function.addToRolePolicy(PolicyStatement.Builder.create()
                .sid("CustomerManagementIam")
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "iam:GetRole",
                        "iam:GetRolePolicy",
                        "iam:GetPolicy",
                        "iam:PassRole",
                        "iam:CreateRole",
                        "iam:CreatePolicy",
                        "iam:AttachRolePolicy"))
                .resources(ImmutableList.of(
                        "arn:aws:iam::" + getAccount() + ":policy/" + CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "*",
                        "arn:aws:iam::" + getAccount() + ":role/" + CUSTOMER_FUN_AND_ROLE_NAME_PREFIX + "*"))
                .build());
    }

    public static void main(String[] args) {
        App app = new App();
        new ControlStack(app);
        app.synth();
    }
}
