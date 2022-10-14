package io.dataspray.stream.control.deploy;

import com.google.common.collect.ImmutableList;
import io.dataspray.lambda.deploy.LambdaBaseStack;
import io.dataspray.stream.control.ControlResource;
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

import static io.dataspray.store.SqsQueueStore.CUSTOMER_QUEUE_WILDCARD;
import static io.dataspray.stream.control.ControlResource.FUN_NAME_WILDCARD;

@Slf4j
public class ControlStack extends LambdaBaseStack {

    public static final String CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME = "customer-function-permission-boundary";
    public static final String CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LOGGING_PREFIX = "CustomerLambdaLogging";
    public static final String CUSTOMER_FUNCTION_PERMISSION_CUSTOMEMR_LAMBDA_SQS = "CustomerLambdaSqs";

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
                                        "arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:/aws/lambda/" + FUN_NAME_WILDCARD + ":*"))
                                .build(),
                        PolicyStatement.Builder.create()
                                .sid(CUSTOMER_FUNCTION_PERMISSION_CUSTOMEMR_LAMBDA_SQS + "PermissionBoundary")
                                .effect(Effect.ALLOW)
                                .actions(ImmutableList.of(
                                        "sqs:ReceiveMessage",
                                        "sqs:DeleteMessage"))
                                .resources(ImmutableList.of(
                                        "arn:aws:sqs:" + getRegion() + ":" + getAccount() + ":" + CUSTOMER_QUEUE_WILDCARD))
                                .build()))
                .build();

        bucketCode = Bucket.Builder.create(this, "control-code-upload-bucket")
                .bucketName(ControlResource.CODE_BUCKET_NAME)
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
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "lambda:CreateFunction",
                        "lambda:UpdateFunctionCode",
                        "lambda:GetFunctionConcurrency",
                        "lambda:GetFunction",
                        "lambda:DeleteFunction",
                        "lambda:PutFunctionConcurrency"))
                .resources(ImmutableList.of(
                        "arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + FUN_NAME_WILDCARD))
                .build());
    }

    public static void main(String[] args) {
        App app = new App();
        new ControlStack(app);
        app.synth();
    }
}
