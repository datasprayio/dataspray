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

package io.dataspray.store.impl;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.primitives.Longs;
import com.google.gson.Gson;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.common.StringUtil;
import io.dataspray.singletable.ShardPageResult;
import io.dataspray.singletable.SingleTable;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import io.dataspray.store.LambdaDeployer;
import io.dataspray.store.LambdaStore;
import io.dataspray.store.LambdaStore.LambdaRecord;
import io.dataspray.store.StreamStore;
import io.dataspray.store.util.CycleUtil;
import io.dataspray.store.util.WaiterUtil;
import io.dataspray.store.util.WithCursor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ConflictException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.Architecture;
import software.amazon.awssdk.services.lambda.model.CreateAliasRequest;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.FunctionUrlAuthType;
import software.amazon.awssdk.services.lambda.model.GetAliasRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.GetPolicyRequest;
import software.amazon.awssdk.services.lambda.model.InvalidParameterValueException;
import software.amazon.awssdk.services.lambda.model.InvokeMode;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsRequest;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PackageType;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.SnapStart;
import software.amazon.awssdk.services.lambda.model.SnapStartApplyOn;
import software.amazon.awssdk.services.lambda.model.UpdateAliasRequest;
import software.amazon.awssdk.services.lambda.model.UpdateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionUrlConfigResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.dataspray.common.DeployEnvironment.DEPLOY_ENVIRONMENT_PROP_NAME;
import static java.util.function.Predicate.not;

@Slf4j
@ApplicationScoped
public class LambdaDeployerImpl implements LambdaDeployer {
    /**
     * There is no AWS limit on compressed package size, this is a server-side enforced sanity check.
     * <p>
     * Also see {@code DataSprayClientImpl.CODE_MAX_SIZE_UNCOMPRESSED_IN_BYTES} enforced on client-side.
     */
    public static final long CODE_MAX_SIZE_COMPRESSED_IN_BYTES = 200 * 1024 * 1024;
    /** Arm is cheaper and also supports Snap-start now */
    public static final Architecture LAMBDA_ARCHITECTURE = Architecture.ARM64;
    public static final int LAMBDA_DEFAULT_TIMEOUT = 128;
    public static final int LAMBDA_DEFAULT_MEMORY_IN_MB = 900;
    /** Lambda version alias pointing to the active version in use */
    public static final String LAMBDA_ACTIVE_QUALIFIER = "ACTIVE";
    public static final String CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME_PROP_NAME = "deployer.customerPermissionBoundary.name";
    public static final String CUSTOMER_FUNCTION_POLICY_PATH_PREFIX = "Customer";
    public static final String CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LOGGING_PREFIX = CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "LambdaLogging";
    public static final String CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_SQS = CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "LambdaSqs";
    public static final String CODE_BUCKET_NAME_PROP_NAME = "deployer.codeBucketName";
    private static final String CODE_KEY_PREFIX = "customer/";
    public static final Function<DeployEnvironment, String> CUSTOMER_FUN_DYNAMO_OR_ROLE_NAME_PREFIX_GETTER = deployEnv ->
            DeployEnvironment.RESOURCE_PREFIX + deployEnv.getSuffix().substring(1 /* Remove duplicate dash */) + "-customer-";
    public static final Function<DeployEnvironment, String> FUN_NAME_WILDCARD_GETTER = deployEnv ->
            CUSTOMER_FUN_DYNAMO_OR_ROLE_NAME_PREFIX_GETTER.apply(deployEnv) + "*";
    private static final String QUEUE_STATEMENT_ID_PREFIX = "customer-queue-statement-for-name-";
    /** Matches io.dataspray.runner.RawCoordinatorImpl.DATASPRAY_API_KEY_ENV */
    public static final String DATASPRAY_API_KEY_ENV = "dataspray_api_key";
    /** Matches io.dataspray.runner.RawCoordinatorImpl.DATASPRAY_ORGANIZATION_NAME_ENV */
    public static final String DATASPRAY_ORGANIZATION_NAME_ENV = "dataspray_organization_name";
    /** Matches io.dataspray.runner.RawCoordinatorImpl.DATASPRAY_ENDPOINT_ENV */
    public static final String DATASPRAY_ENDPOINT_ENV = "dataspray_endpoint";
    /** Matches io.dataspray.runner.StateManagerFactoryImpl.DATASPRAY_STATE_TABLE_NAME_ENV */
    public static final String DATASPRAY_STATE_TABLE_NAME_ENV = "dataspray_state_table_name";

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = "aws.region")
    String awsRegion;
    @ConfigProperty(name = DEPLOY_ENVIRONMENT_PROP_NAME)
    DeployEnvironment deployEnv;
    @ConfigProperty(name = CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME_PROP_NAME, defaultValue = "customer-permission-boundary")
    String customerFunctionPermissionBoundaryName;
    @ConfigProperty(name = CODE_BUCKET_NAME_PROP_NAME, defaultValue = "io-dataspray-code-upload")
    String codeBucketName;

    @Inject
    IamClient iamClient;
    @Inject
    LambdaClient lambdaClient;
    @Inject
    DynamoDbClient dynamoClient;
    @Inject
    LambdaStore lambdaStore;
    @Inject
    S3Presigner s3Presigner;
    @Inject
    StreamStore streamStore;
    @Inject
    WaiterUtil waiterUtil;
    @Inject
    Gson gson;
    @Inject
    ApiAccessStore apiAccessStore;

    @SneakyThrows
    @Override
    public DeployedVersion deployVersion(
            String organizationName,
            String username,
            Optional<String> apiEndpointOpt,
            String taskId,
            String codeUrl,
            String handler,
            ImmutableSet<String> inputQueueNames,
            ImmutableSet<String> outputQueueNames,
            Runtime runtime,
            Optional<Endpoint> endpointOpt,
            Optional<DynamoState> dynamoState,
            boolean switchToImmediately) {
        try (AutoCloseable lock = lambdaStore.acquireLock(organizationName, taskId)
                .orElseThrow(() -> new ConflictException("Task is already locked for editing by another process, try again later."))) {
            return deployVersionInternal(
                    organizationName,
                    username,
                    apiEndpointOpt,
                    taskId,
                    codeUrl,
                    handler,
                    inputQueueNames,
                    outputQueueNames,
                    runtime,
                    endpointOpt,
                    dynamoState,
                    switchToImmediately);
        }
    }

    @SneakyThrows
    private DeployedVersion deployVersionInternal(
            String organizationName,
            String username,
            Optional<String> apiEndpointOpt,
            String taskId,
            String codeUrl,
            String handler,
            ImmutableSet<String> inputQueueNames,
            ImmutableSet<String> outputQueueNames,
            Runtime runtime,
            Optional<Endpoint> endpointOpt,
            Optional<DynamoState> dynamoState,
            boolean switchToImmediately) {

        // Check for loops between deployed Tasks and current update/creation of this task.
        // Note that this is a best-effort check to prevent customers from shooting themselves in the foot.
        // This check is not perfect as a concurrent deploy to another task could cause a loop.
        // A task could also trigger an event outside the declared queue outputs that may cause a loop.
        Optional<List<CycleUtil.Node>> cycleOpt = lambdaStore.checkLoops(organizationName, taskId, inputQueueNames, outputQueueNames);
        if (cycleOpt.isPresent()) {
            String cycleDescription = "Cycle detected between tasks: " + cycleOpt.get().stream()
                    .map(n -> n.getName() + " [IN: " + n.getNodeInputs() + ", OUT: " + n.getNodeOutputs() + "]")
                    .collect(Collectors.joining(", "));
            log.info("Deploy task {} has a cycle in org {}: {}", taskId, organizationName, cycleDescription);
            throw new ConflictException(cycleDescription);
        }

        String functionName = getFunctionName(organizationName, taskId);

        // Check whether function exists
        Optional<FunctionConfiguration> existingFunctionOpt;
        try {
            existingFunctionOpt = Optional.of(lambdaClient.getFunction(GetFunctionRequest.builder()
                            .functionName(functionName)
                            .build())
                    .configuration());
            log.debug("Found existing function {}", existingFunctionOpt.get().functionName());
        } catch (ResourceNotFoundException ex) {
            existingFunctionOpt = Optional.empty();
            log.info("Function doesn't yet exist {}", functionName);
        }

        // Setup function IAM role
        String functionRoleName = getFunctionRoleName(organizationName, taskId);
        String functionRoleArn = "arn:aws:iam::" + awsAccountId + ":role/" + functionRoleName;
        try {
            iamClient.getRole(GetRoleRequest.builder()
                    .roleName(functionRoleName)
                    .build());
            log.debug("Found role {}", functionRoleName);
        } catch (NoSuchEntityException ex2) {
            iamClient.createRole(CreateRoleRequest.builder()
                    .roleName(functionRoleName)
                    .description("Auto-created for Lambda " + functionName)
                    .permissionsBoundary("arn:aws:iam::" + awsAccountId + ":policy/" + customerFunctionPermissionBoundaryName)
                    .assumeRolePolicyDocument(gson.toJson(Map.of(
                            "Version", "2012-10-17",
                            "Statement", List.of(Map.of(
                                    "Effect", "Allow",
                                    "Action", List.of("sts:AssumeRole"),
                                    "Principal", Map.of(
                                            "Service", List.of("lambda.amazonaws.com")))))))
                    .build());
            log.info("Created role {}", functionRoleName);
            waiterUtil.resolve(iamClient.waiter().waitUntilRoleExists(GetRoleRequest.builder()
                    .roleName(functionRoleName)
                    .build()));
        }

        // Lambda logging policy: get or create policy, then attach to role if needed
        ensurePolicyAttachedToRole(functionRoleName,
                CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LOGGING_PREFIX + StringUtil.camelCase(functionName, true),
                gson.toJson(Map.of(
                        "Version", "2012-10-17",
                        "Statement", List.of(Map.of(
                                "Effect", "Allow",
                                "Action", List.of(
                                        "logs:CreateLogGroup",
                                        "logs:CreateLogStream",
                                        "logs:PutLogEvents"),
                                "Resource", List.of(
                                        "arn:aws:logs:" + awsRegion + ":" + awsAccountId + ":log-group:/aws/lambda/" + FUN_NAME_WILDCARD_GETTER.apply(deployEnv),
                                        "arn:aws:logs:" + awsRegion + ":" + awsAccountId + ":log-group:/aws/lambda/" + FUN_NAME_WILDCARD_GETTER.apply(deployEnv) + ":*"
                                ))))));

        // Determine Architecture and SnapStart setting
        final SnapStartApplyOn snapStartApplyOn = switch (runtime) {
            // Supported runtimes: https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html#snapstart-runtimes
            case JAVA11, JAVA17, JAVA21 -> SnapStartApplyOn.PUBLISHED_VERSIONS;
            default -> SnapStartApplyOn.NONE;
        };

        // Generate an API key for task
        // This key does not yet have any access, that will be persisted later once we know the published task version.
        String apiKey = apiAccessStore.generateApiKey();

        // Prepare SingleTable ahead of time to generate table name
        Optional<SingleTable> customerSingleTableOpt = dynamoState.map(s -> SingleTable.builder()
                .tableName(getFunctionOrDynamoOrRoleNamePrefix(organizationName))
                .overrideGson(gson)
                .build());

        // Create or update function configuration and code
        final String publishedVersion;
        final String publishedDescription = generateVersionDescription(taskId, inputQueueNames, outputQueueNames, endpointOpt);
        ImmutableMap.Builder<String, String> envBuilder = ImmutableMap.<String, String>builder()
                .put(DATASPRAY_API_KEY_ENV, apiKey)
                .put(DATASPRAY_ORGANIZATION_NAME_ENV, organizationName);
        apiEndpointOpt.ifPresent(endpoint -> envBuilder
                .put(DATASPRAY_ENDPOINT_ENV, endpoint));
        customerSingleTableOpt.ifPresent(customerSingleTable -> envBuilder
                .put(DATASPRAY_STATE_TABLE_NAME_ENV, customerSingleTable.getTableName()));
        Environment env = Environment.builder()
                .variables(envBuilder.build()).build();
        if (existingFunctionOpt.isEmpty()) {
            Retryer<String> retryer = RetryerBuilder.<String>newBuilder()
                    // Creating a role requires some time even when using a waiter https://stackoverflow.com/a/37438525
                    .retryIfException(ex -> ex instanceof InvalidParameterValueException
                                            && ex.getMessage().contains("The role defined for the function cannot be assumed by Lambda."))
                    .withStopStrategy(StopStrategies.stopAfterDelay(1, TimeUnit.MINUTES))
                    .withWaitStrategy(WaitStrategies.exponentialWait(2, 15, TimeUnit.SECONDS))
                    .build();
            // Create a new function with configuration and code all in one go
            publishedVersion = retryer.call(() -> lambdaClient.createFunction(CreateFunctionRequest.builder()
                            .publish(true)
                            .functionName(functionName)
                            .description(publishedDescription)
                            .role(functionRoleArn)
                            .packageType(PackageType.ZIP)
                            .architectures(LAMBDA_ARCHITECTURE)
                            .code(FunctionCode.builder()
                                    .s3Bucket(codeBucketName)
                                    .s3Key(getCodeKeyFromUrl(organizationName, codeUrl))
                                    .build())
                            .runtime(runtime)
                            .handler(handler)
                            .environment(env)
                            .memorySize(LAMBDA_DEFAULT_MEMORY_IN_MB)
                            .timeout(LAMBDA_DEFAULT_TIMEOUT)
                            .snapStart(SnapStart.builder()
                                    .applyOn(snapStartApplyOn)
                                    .build())
                            .build())
                    .version());
            log.info("Created function {} with published version {} description {}", functionName, publishedVersion, publishedDescription);

            // Wait until function version publishes
            waiterUtil.resolve(lambdaClient.waiter().waitUntilFunctionExists(GetFunctionRequest.builder()
                    .functionName(functionName)
                    .qualifier(publishedVersion)
                    .build()));
        } else {
            // Update function configuration
            lambdaClient.updateFunctionConfiguration(UpdateFunctionConfigurationRequest.builder()
                    .functionName(functionName)
                    // Description always changes with the latest timestamp
                    .description(publishedDescription)
                    .role(functionRoleArn)
                    .runtime(runtime)
                    .handler(handler)
                    .environment(env)
                    .memorySize(LAMBDA_DEFAULT_MEMORY_IN_MB)
                    .snapStart(SnapStart.builder()
                            .applyOn(snapStartApplyOn)
                            .build())
                    .revisionId(existingFunctionOpt.get().revisionId())
                    .build());
            log.info("Updated function configuration {} with description {}", functionName, publishedDescription);

            // Wait until updated
            waiterUtil.resolve(lambdaClient.waiter().waitUntilFunctionUpdatedV2(GetFunctionRequest.builder()
                    .functionName(functionName)
                    .build()));

            // Update function code
            publishedVersion = lambdaClient.updateFunctionCode(UpdateFunctionCodeRequest.builder()
                            .publish(true)
                            .functionName(functionName)
                            .architectures(LAMBDA_ARCHITECTURE)
                            .s3Bucket(codeBucketName)
                            .s3Key(getCodeKeyFromUrl(organizationName, codeUrl))
                            // updateFunctionConfiguration returns invalid revisionId
                            // https://github.com/aws/aws-sdk/issues/377
                            // .revisionId(updateFunctionRevisionId)
                            .build())
                    .version();
            log.info("Updated function code {} published version {}", functionName, publishedVersion);

            // Wait until updated
            waiterUtil.resolve(lambdaClient.waiter().waitUntilFunctionUpdatedV2(GetFunctionRequest.builder()
                    .functionName(functionName)
                    .qualifier(publishedVersion)
                    .build()));
        }

        // Check if function has a Function URL
        Optional<GetFunctionUrlConfigResponse> functionUrlConfigOpt;
        try {
            functionUrlConfigOpt = Optional.of(lambdaClient.getFunctionUrlConfig(GetFunctionUrlConfigRequest.builder()
                    .functionName(functionName)
                    .qualifier(publishedVersion)
                    .build()));
        } catch (ResourceNotFoundException ex) {
            // Does not have a Function URL
            functionUrlConfigOpt = Optional.empty();
        }
        final Optional<String> endpointUrlOpt;
        if (endpointOpt.isEmpty()) {
            endpointUrlOpt = Optional.empty();
            // We don't want a Function URL
            if (functionUrlConfigOpt.isEmpty()) {
                // And it doesn't exist, nothing to do
                log.debug("No function url needed");
            } else {
                // And it exists, we need to delete it
                lambdaClient.deleteFunctionUrlConfig(DeleteFunctionUrlConfigRequest.builder()
                        .functionName(functionName)
                        .qualifier(publishedVersion)
                        .build());
                log.info("Deleted function url {}", functionUrlConfigOpt.get().functionUrl());
            }
        } else {
            // We want a Function URL
            Endpoint endpoint = endpointOpt.get();
            if (functionUrlConfigOpt.isEmpty()) {
                // And it doesn't exist, we need to create it
                CreateFunctionUrlConfigResponse createFunctionUrlConfigResponse = lambdaClient.createFunctionUrlConfig(CreateFunctionUrlConfigRequest.builder()
                        .functionName(functionName)
                        .qualifier(publishedVersion)
                        .authType(endpoint.isPublic()
                                ? FunctionUrlAuthType.NONE
                                : FunctionUrlAuthType.AWS_IAM)
                        .cors(endpoint.getCors().orElse(null))
                        .invokeMode(InvokeMode.BUFFERED)
                        .build());
                endpointUrlOpt = Optional.of(createFunctionUrlConfigResponse.functionUrl());
                log.info("Created function url {}", endpointUrlOpt.get());
            } else {
                // And it already exists, we may have to update it
                GetFunctionUrlConfigResponse config = functionUrlConfigOpt.get();
                if (config.authType().equals(FunctionUrlAuthType.NONE) == endpoint.isPublic()
                    || config.invokeMode() != InvokeMode.BUFFERED
                    || !Optional.ofNullable(config.cors()).equals(endpoint.getCors())) {

                    // Settings do not match, update it all
                    UpdateFunctionUrlConfigResponse updateFunctionUrlConfigResponse = lambdaClient.updateFunctionUrlConfig(UpdateFunctionUrlConfigRequest.builder()
                            .functionName(functionName)
                            .qualifier(publishedVersion)
                            .authType(endpoint.isPublic()
                                    ? FunctionUrlAuthType.NONE
                                    : FunctionUrlAuthType.AWS_IAM)
                            .cors(endpoint.getCors().orElse(null))
                            .invokeMode(InvokeMode.BUFFERED)
                            .build());
                    endpointUrlOpt = Optional.of(updateFunctionUrlConfigResponse.functionUrl());
                    log.info("Updated function url {}", endpointUrlOpt.get());
                } else {
                    endpointUrlOpt = Optional.of(functionUrlConfigOpt.get().functionUrl());
                    log.debug("No update needed for function url {}", endpointUrlOpt.get());
                }
            }
        }

        // Record the Lambda function now that we have the endpoint
        lambdaStore.set(
                organizationName,
                taskId,
                username,
                inputQueueNames,
                outputQueueNames,
                endpointUrlOpt);

        // Persist the Api Key now that we know the task's published version
        ApiAccess apiAccess = apiAccessStore.createApiAccessForTask(
                apiKey,
                organizationName,
                publishedDescription,
                username,
                taskId,
                publishedVersion,
                ApiAccessStore.UsageKeyType.ORGANIZATION,
                Optional.of(outputQueueNames));

        // Create Dynamo for lambda state if needed
        if (dynamoState.isPresent() && customerSingleTableOpt.isPresent()) {
            customerSingleTableOpt.get().createTableIfNotExists(
                    dynamoClient,
                    dynamoState.get().getLsiCount().intValue(),
                    dynamoState.get().getGsiCount().intValue());

            // Give permission to Dynamo
            String tableName = customerSingleTableOpt.get().getTableName();
            String lambdaDynamoPolicyName = CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_SQS + StringUtil.camelCase(functionName, true) + "Dynamo" + tableName;
            ensurePolicyAttachedToRole(functionRoleName, lambdaDynamoPolicyName, gson.toJson(Map.of(
                    "Version", "2012-10-17",
                    "Statement", List.of(Map.of(
                            "Effect", "Allow",
                            "Action", List.of(
                                    "dynamodb:GetItem",
                                    "dynamodb:BatchGetItem",
                                    "dynamodb:Query",
                                    "dynamodb:PutItem",
                                    "dynamodb:UpdateItem",
                                    "dynamodb:BatchWriteItem",
                                    "dynamodb:DeleteItem"),
                            "Resource", List.of(
                                    "arn:aws:dynamodb:" + awsRegion + ":" + awsAccountId + ":table/" + tableName,
                                    "arn:aws:dynamodb:" + awsRegion + ":" + awsAccountId + ":table/" + tableName + "/index/*"
                            ))))));
        }

        // Create active alias if doesn't exist
        boolean aliasAlreadyExists;
        try {
            lambdaClient.createAlias(CreateAliasRequest.builder()
                    .functionName(functionName)
                    .functionVersion(publishedVersion)
                    .name(LAMBDA_ACTIVE_QUALIFIER)
                    .build());
            log.info("Created function {} alias {} with version {}", functionName, LAMBDA_ACTIVE_QUALIFIER, publishedVersion);
            aliasAlreadyExists = false;
        } catch (LambdaException ex) {
            if (ex instanceof ResourceConflictException
                // Moto behavior used in testing
                || "ConflictException".equals(ex.awsErrorDetails().errorCode())) {

                aliasAlreadyExists = true;
                log.debug("Alias {} already exists for function {}", LAMBDA_ACTIVE_QUALIFIER, functionName);
            } else {
                throw ex;
            }
        }

        // Add queue permissions on the deployed function version
        // Although this is not necessary, as the versioned function is never invoked directly,
        // rather the ACTIVE alias is invoked. However, this is a somewhat elegant way to keep
        // track of function version <--> queue names for later switchover/rollback/resume
        for (String inputQueueName : inputQueueNames) {
            addQueuePermissionToFunction(organizationName, taskId, publishedVersion, inputQueueName);
        }

        // In the next section, we need to create new sources disabled OR if we are switching to this version now
        // need to perform the entire switchover now. Firstly we will prepare the new queues, then perform the switchover
        // with the only difference with new queues will be we will create them already enabled.
        ImmutableSet<QueueSource> queueSources = getTaskQueueSources(organizationName, taskId);
        Set<String> missingQueueSources = Sets.difference(inputQueueNames, queueSources.stream().map(QueueSource::getQueueName).collect(Collectors.toSet()));

        // Prepare new queue sources, not creating just yet.
        for (String queueNameToAdd : missingQueueSources) {

            // Create queue if it doesn't exist
            if (!streamStore.streamExists(organizationName, queueNameToAdd)) {
                streamStore.createStream(organizationName, queueNameToAdd);
                log.info("Created queue {}", queueNameToAdd);
            }

            // Add the permission for Event Source Mapping on the active qualifier
            addQueuePermissionToFunction(organizationName, taskId, LAMBDA_ACTIVE_QUALIFIER, queueNameToAdd);

            // Lambda logging policy: get or create policy, then attach to role if needed
            String lambdaSqsPolicyName = CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_SQS + StringUtil.camelCase(functionName, true) + "Queue" + StringUtil.camelCase(queueNameToAdd, true);
            ensurePolicyAttachedToRole(functionRoleName, lambdaSqsPolicyName, gson.toJson(Map.of(
                    "Version", "2012-10-17",
                    "Statement", List.of(Map.of(
                            "Effect", "Allow",
                            "Action", List.of(
                                    "sqs:ChangeMessageVisibility",
                                    "sqs:ReceiveMessage",
                                    "sqs:DeleteMessage",
                                    "sqs:GetQueueAttributes"),
                            "Resource", List.of(
                                    "arn:aws:sqs:" + awsRegion + ":" + awsAccountId + ":" + streamStore.getAwsQueueName(organizationName, queueNameToAdd)))))));
        }

        if (switchToImmediately) {
            log.info("Switching over to deployed task {} version {}", taskId, publishedVersion);
            // Disable unneeded sources
            queueSources.stream()
                    .filter(source -> !inputQueueNames.contains(source.getQueueName()))
                    .forEach(source -> disableSource(taskId, source, "switchover on deploy"));

            // Switch active tag
            // No need to switch if we just created it without published version
            if (aliasAlreadyExists) {
                log.info("Updating task {} alias {} to version {} part of switchover on deploy", taskId, LAMBDA_ACTIVE_QUALIFIER, publishedVersion);
                lambdaClient.updateAlias(UpdateAliasRequest.builder()
                        .functionName(functionName)
                        .functionVersion(publishedVersion)
                        .name(LAMBDA_ACTIVE_QUALIFIER)
                        .build());
            }

            //  Enable new sources
            queueSources.stream()
                    .filter(source -> inputQueueNames.contains(source.getQueueName()))
                    .forEach(source -> enableSource(taskId, source, "switchover on deploy"));
        }

        // Link SQS with Lambda
        for (String queueNameToAdd : missingQueueSources) {
            // Add the Event Source Mapping
            String sourceUuid = lambdaClient.createEventSourceMapping(CreateEventSourceMappingRequest.builder()
                            // ARN is needed if we want to supply qualifier
                            .functionName(getFunctionArn(functionName, LAMBDA_ACTIVE_QUALIFIER))
                            .enabled(switchToImmediately)
                            .batchSize(1)
                            .eventSourceArn("arn:aws:sqs:" + awsRegion + ":" + awsAccountId + ":" + streamStore.getAwsQueueName(organizationName, queueNameToAdd))
                            .build())
                    .uuid();
            log.info("Created function {}:{} event source mapping for queue {}", functionName, LAMBDA_ACTIVE_QUALIFIER, queueNameToAdd);
        }

        return new DeployedVersion(
                publishedVersion,
                publishedDescription,
                endpointUrlOpt);
    }

    @Override
    public void switchVersion(String organizationName, String taskId, String version) {
        // Gather info about the source and destination versions
        String functionName = getFunctionName(organizationName, taskId);
        ImmutableSet<String> queueNames = readQueueNamesFromFunctionPermissions(organizationName, taskId, version);
        ImmutableSet<QueueSource> queueSources = getTaskQueueSources(organizationName, taskId);

        // Ensure all event sources are present before switchover
        Set<String> missingQueueSources = Sets.difference(queueNames, queueSources.stream()
                .map(QueueSource::getQueueName)
                .collect(ImmutableSet.toImmutableSet()));
        if (!missingQueueSources.isEmpty()) {
            log.warn("Cannot switch task {} to version {}, missing event source mappings {}", taskId, version, missingQueueSources);
            throw new InternalServerErrorException("Missing source queue, failed to switchover, please re-deploy");
        }

        // Disable unneeded sources
        queueSources.stream()
                .filter(source -> !queueNames.contains(source.getQueueName()))
                .forEach(source -> disableSource(taskId, source, "switchover"));

        // Switch active tag
        log.info("Updating task {} alias {} to version {} part of switchover", taskId, LAMBDA_ACTIVE_QUALIFIER, version);
        lambdaClient.updateAlias(UpdateAliasRequest.builder()
                .functionName(functionName)
                .functionVersion(version)
                .name(LAMBDA_ACTIVE_QUALIFIER)
                .build());

        //  Enable new sources
        queueSources.stream()
                .filter(source -> queueNames.contains(source.getQueueName()))
                .forEach(source -> enableSource(taskId, source, "switchover"));
    }

    @Override
    public Versions getVersions(String organizationName, String taskId) {
        LambdaRecord lambdaRecord = lambdaStore.get(organizationName, taskId)
                .orElseThrow(() -> new NotFoundException("Task does not exist: " + taskId));

        String functionName = getFunctionName(organizationName, taskId);

        // Find active version via alias
        Status activeStatus = status(organizationName, taskId)
                .orElseThrow(() -> new NotFoundException("Task does not exist: " + taskId));

        // Fetch all versions
        ImmutableSet<LambdaVersion> versions = lambdaClient.listVersionsByFunctionPaginator(ListVersionsByFunctionRequest.builder()
                        .functionName(functionName)
                        .build())
                .stream()
                .map(ListVersionsByFunctionResponse::versions)
                .flatMap(Collection::stream)
                .filter(f -> !"$LATEST".equals(f.version()))
                .map(functionConfiguration -> new LambdaVersion(
                        functionConfiguration.version(),
                        functionConfiguration.description()))
                .collect(ImmutableSet.toImmutableSet());

        // Clean up old versions
        String activeVersionToKeep = activeStatus.getFunction().version();
        ImmutableSet<String> taskVersionsToDelete = versions.stream()
                .map(LambdaVersion::getVersion)
                .filter(not(activeVersionToKeep::equals))
                .map(Longs::tryParse)
                .filter(Objects::nonNull)
                .mapToLong(i -> i)
                .skip(2)
                .mapToObj(String::valueOf)
                .collect(ImmutableSet.toImmutableSet());
        taskVersionsToDelete.forEach(taskVersionToDelete -> {
            lambdaClient.deleteFunction(DeleteFunctionRequest.builder()
                    .functionName(functionName)
                    .qualifier(taskVersionToDelete)
                    .build());
            apiAccessStore.revokeApiKeyForTaskVersion(organizationName, taskId, taskVersionToDelete);
        });

        return new Versions(
                activeStatus,
                versions.stream()
                        // Don't include the versions we just cleaned up
                        .filter(version -> !taskVersionsToDelete.contains(version.getVersion()))
                        .collect(ImmutableMap.toImmutableMap(
                                LambdaVersion::getVersion,
                                version -> version)),
                lambdaRecord.getEndpointUrlOpt());
    }

    @Override
    public Optional<Status> status(String organizationName, String taskId) {
        return lambdaStore.get(organizationName, taskId)
                .flatMap(this::status);
    }

    private Optional<Status> status(LambdaRecord lambdaRecord) {
        String organizationName = lambdaRecord.getOrganizationName();
        String taskId = lambdaRecord.getTaskId();

        Optional<String> activeVersionOpt = fetchActiveVersion(organizationName, taskId);
        if (activeVersionOpt.isEmpty()) {
            log.debug("Task {} has no active version", taskId);
            return Optional.empty();
        }

        String functionName = getFunctionName(organizationName, taskId);
        FunctionConfiguration function;
        try {
            function = lambdaClient.getFunction(GetFunctionRequest.builder()
                            .functionName(functionName)
                            .qualifier(activeVersionOpt.get())
                            .build())
                    .configuration();
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }

        ImmutableSet<String> queueNames = lambdaRecord.getInputQueueNames();
        final State state;
        if (queueNames.isEmpty()) {
            state = State.PAUSED;
            log.debug("Function {} version {} has no queues, considering {}", functionName, activeVersionOpt.get(), state);
        } else {
            ImmutableMap<String, QueueSource> queueSources = getTaskQueueSources(organizationName, taskId).stream()
                    .collect(ImmutableMap.toImmutableMap(QueueSource::getQueueName, s -> s));

            ImmutableMap<String, Optional<State>> queueNamesToState = queueNames.stream()
                    .collect(ImmutableMap.toImmutableMap(
                            queueName -> queueName,
                            queueName -> Optional.ofNullable(queueSources.get(queueName))
                                    .map(QueueSource::getState)
                    ));
            state = queueNamesToState.values().stream()
                    .map(s -> s.orElse(State.PAUSED))
                    .max(Comparator.comparing(State::getWeight))
                    .orElse(State.RUNNING);
            log.debug("Function {} version {} has state {} due to queues states' {}",
                    functionName, activeVersionOpt.get(), state, queueNamesToState);
        }

        return Optional.of(new Status(
                taskId,
                function,
                state,
                lambdaRecord.getEndpointUrlOpt()));
    }

    @Override
    public WithCursor<ImmutableList<Status>> statusAll(String organizationName, String cursor) {
        ShardPageResult<LambdaRecord> page = lambdaStore.getForOrganization(organizationName, false, Optional.ofNullable(Strings.emptyToNull(cursor)));
        return new WithCursor<>(
                page.getItems()
                        .stream()
                        .map(this::status)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(ImmutableList.toImmutableList()),
                page.getCursorOpt());
    }

    @Override
    public void pause(String organizationName, String taskId) {
        //  Disable all queue sources
        getTaskQueueSources(organizationName, taskId)
                .forEach(source -> disableSource(taskId, source, "pause"));
    }

    @Override
    public void resume(String organizationName, String taskId) {
        String activeVersion = fetchActiveVersion(organizationName, taskId).orElseThrow(() -> new BadRequestException("Task not active"));
        ImmutableSet<String> queueNames = readQueueNamesFromFunctionPermissions(organizationName, taskId, activeVersion);
        ImmutableSet<QueueSource> queueSources = getTaskQueueSources(organizationName, taskId);

        // Just in case, disable any queues that aren't supposed to be enabled in the first place
        queueSources.stream()
                .filter(source -> !queueNames.contains(source.getQueueName()))
                .forEach(source -> disableSource(taskId, source, "resuming found wrongly enabled queues"));

        // Resume sources
        queueSources.stream()
                .filter(source -> queueNames.contains(source.getQueueName()))
                .forEach(source -> enableSource(taskId, source, "resume"));
    }

    @Override
    public void delete(String organizationName, String taskId) {
        lambdaClient.deleteFunction(DeleteFunctionRequest.builder()
                .functionName(getFunctionName(organizationName, taskId))
                .build());
        apiAccessStore.revokeApiKeysForTaskId(organizationName, taskId);
        lambdaStore.markDeleted(organizationName, taskId);
    }

    @Override
    public UploadCodeClaim uploadCode(String customerId, String taskId, long contentLengthBytes) {
        if (contentLengthBytes <= 0) {
            throw new BadRequestException("Cannot upload empty file.");
        }
        if (contentLengthBytes > CODE_MAX_SIZE_COMPRESSED_IN_BYTES) {
            throw new BadRequestException("Maximum compressed code size is " + CODE_MAX_SIZE_COMPRESSED_IN_BYTES / 1024 / 1024 + "MB, but found " + contentLengthBytes / 1024 / 1024 + "MB, please contact support");
        }
        String key = getCodeKeyPrefix(customerId)
                     + taskId
                     + "-"
                     + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS").withZone(ZoneOffset.UTC).format(Instant.now()) + ".zip";
        String codeUrl = "s3://" + codeBucketName + "/" + key;
        String presignedUrl = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(codeBucketName)
                                .key(key)
                                .contentLength(contentLengthBytes)
                                .contentType("application/zip")
                                .build())
                        .signatureDuration(Duration.ofHours(1))
                        .build())
                .url()
                .toExternalForm();
        return new UploadCodeClaim(presignedUrl, codeUrl);
    }

    private String getFunctionArn(String functionName, String qualifier) {
        return "arn:aws:lambda:" + awsRegion + ":" + awsAccountId + ":function:" + functionName + ":" + qualifier;
    }

    private String getFunctionName(String organizationName, String taskId) {
        return getFunctionOrDynamoOrRoleNamePrefix(organizationName) + taskId;
    }

    private String getFunctionRoleName(String organizationName, String taskId) {
        return getFunctionName(organizationName, taskId) + "-role";
    }

    private String getFunctionOrDynamoOrRoleNamePrefix(String organizationName) {
        return CUSTOMER_FUN_DYNAMO_OR_ROLE_NAME_PREFIX_GETTER.apply(deployEnv) + organizationName + "-";
    }

    private Optional<String> getTaskIdFromFunctionName(String customerId, String functionName) {
        String functionPrefix = getFunctionOrDynamoOrRoleNamePrefix(customerId);
        return functionName.startsWith(functionPrefix)
                ? Optional.of(functionName.substring(functionPrefix.length()))
                : Optional.empty();
    }

    private String generateVersionDescription(String taskId, ImmutableSet<String> inputQueueNames, ImmutableSet<String> outputQueueNames, Optional<Endpoint> endpointOpt) {
        return "Task " + taskId
               + " inputs [" + String.join(", ", inputQueueNames) + "]"
               + " outputs [" + String.join(", ", outputQueueNames) + "]"
               + " endpoint " + endpointOpt.isPresent()
               + " deployed on " + Instant.now();
    }

    private String getCodeKeyPrefix(String customerId) {
        return CODE_KEY_PREFIX + customerId + "/";
    }

    private String getCodeKeyFromUrl(String customerId, String url) {
        String s3UrlAndBucketPrefix = "s3://" + codeBucketName + "/";
        String s3UrlAndBucketAndKeyPrefix = s3UrlAndBucketPrefix + getCodeKeyPrefix(customerId);
        if (!url.startsWith(s3UrlAndBucketAndKeyPrefix)) {
            throw new BadRequestException("Incorrect bucket location");
        }
        return url.substring(s3UrlAndBucketPrefix.length());
    }

    private void enableSource(String taskId, QueueSource source, String reason) {
        if (source.getState().getIsFinalStateRunningOpt().isPresent()
            && source.getState().getIsFinalStateRunningOpt().get()) {
            return;
        }
        if (source.getState().isUpdating()
            || !source.getState().getIsFinalStateRunningOpt().isPresent()) {
            throw new ConflictException("Another operation is in progress: "
                                        + source.getQueueName() + " is in state " + source.getState());
        }
        log.info("Enabling task {} source {} uuid {}: {}",
                taskId, source.getQueueName(), source.getUuid(), reason);
        lambdaClient.updateEventSourceMapping(UpdateEventSourceMappingRequest.builder()
                .uuid(source.getUuid())
                .enabled(true)
                .build());
    }

    private void disableSource(String taskId, QueueSource source, String reason) {
        if (source.getState().getIsFinalStateRunningOpt().isPresent()
            && !source.getState().getIsFinalStateRunningOpt().get()) {
            return;
        }
        if (source.getState().isUpdating()
            || !source.getState().getIsFinalStateRunningOpt().isPresent()) {
            throw new ConflictException("Another operation is in progress: "
                                        + source.getQueueName() + " is in state " + source.getState());
        }
        log.info("Disabling task {} source {} uuid {}: {}",
                taskId, source.getQueueName(), source.getUuid(), reason);
        lambdaClient.updateEventSourceMapping(UpdateEventSourceMappingRequest.builder()
                .uuid(source.getUuid())
                .enabled(false)
                .build());
    }

    private void ensurePolicyAttachedToRole(String roleName, String policyName, String policyDocument) {
        try {
            // First see if policy is attached to the role already
            iamClient.getRolePolicy(GetRolePolicyRequest.builder()
                    .roleName(roleName)
                    .policyName(policyName)
                    .build());
            log.debug("Found role {} policy {}", roleName, policyName);
        } catch (NoSuchEntityException ex) {
            iamClient.putRolePolicy(PutRolePolicyRequest.builder()
                    .roleName(roleName)
                    .policyName(policyName)
                    .policyDocument(policyDocument)
                    .build());
            log.info("Created role {} policy {}", roleName, policyName);
            waiterUtil.resolve(waiterUtil.waitUntilPolicyAttachedToRole(roleName, policyName));
        }
    }

    private Optional<String> fetchActiveVersion(String organizationName, String taskId) {
        // Find active version via alias
        try {
            return Optional.of(lambdaClient.getAlias(GetAliasRequest.builder()
                            .functionName(getFunctionName(organizationName, taskId))
                            .name(LAMBDA_ACTIVE_QUALIFIER)
                            .build())
                    .functionVersion());
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }
    }

    private void addQueuePermissionToFunction(String customerId, String taskId, String qualifier, String queueName) {
        String functionName = getFunctionName(customerId, taskId);
        try {
            lambdaClient.addPermission(AddPermissionRequest.builder()
                    .functionName(functionName)
                    .qualifier(qualifier)
                    .statementId(getQueueStatementId(queueName))
                    .principal("sqs.amazonaws.com")
                    .action("lambda:InvokeFunction")
                    .sourceArn("arn:aws:sqs:" + awsRegion + ":" + awsAccountId + ":" + streamStore.getAwsQueueName(customerId, queueName))
                    .build());
            log.debug("Created function {} permission for qualifier {} and queue {}", functionName, qualifier, queueName);
        } catch (ResourceConflictException ex) {
            log.warn("Function {}:{} permission for queue {} already exists, ignoring", functionName, qualifier, queueName);
        }
    }

    /**
     * Read queue names from permissions, given version. For a list of names for active version, use
     * {@link LambdaStore#get} instead.
     */
    private ImmutableSet<String> readQueueNamesFromFunctionPermissions(String customerId, String taskId, String version) {
        String functionName = getFunctionName(customerId, taskId);
        String policyStr = lambdaClient.getPolicy(GetPolicyRequest.builder()
                        .functionName(getFunctionArn(functionName, version))
                        .build())
                .policy();
        log.trace("Fetched policy for function {} version {}: {}", functionName, version, policyStr);
        ResourcePolicyDocument resourcePolicyDocument = gson.fromJson(policyStr, ResourcePolicyDocument.class);
        if (!"2012-10-17".equals(resourcePolicyDocument.getVersion())) {
            log.error("Cannot parse resource policy document with unknown version {} for customer {} taskId {} version {}",
                    resourcePolicyDocument.getVersion(), customerId, taskId, version);
            throw new InternalServerErrorException("Cannot parse resource, please contact support");
        }
        ImmutableSet<String> queueNames = resourcePolicyDocument
                .getStatements().stream()
                // Moto behavior: need to also filter out all other versions
                .filter(statement -> statement.getResource()
                        .endsWith(":" + version))
                .map(ResourcePolicyStatement::getStatementId)
                .map(this::getQueueNameFromStatementId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(ImmutableSet.toImmutableSet());
        log.trace("Fetched customer {} task {} version {} queue names from policy {}", customerId, taskId, version, queueNames);
        return queueNames;
    }

    private ImmutableSet<QueueSource> getTaskQueueSources(String customerId, String taskId) {
        ImmutableSet<QueueSource> queueSources = lambdaClient.listEventSourceMappingsPaginator(ListEventSourceMappingsRequest.builder()
                        // ARN is needed if we want to supply qualifier
                        .functionName(getFunctionArn(getFunctionName(customerId, taskId), LAMBDA_ACTIVE_QUALIFIER))
                        .build())
                .stream()
                .map(ListEventSourceMappingsResponse::eventSourceMappings)
                .flatMap(Collection::stream)
                .flatMap(source -> {
                    String arnPrefix = "arn:aws:sqs:" + awsRegion + ":" + awsAccountId + ":";
                    if (!source.eventSourceArn().startsWith(arnPrefix)) {
                        return Stream.of();
                    }
                    String awsQueueName = source.eventSourceArn().substring(arnPrefix.length());
                    Optional<String> queueNameOpt = streamStore.extractStreamNameFromAwsQueueName(customerId, awsQueueName);
                    if (queueNameOpt.isEmpty()) {
                        return Stream.of();
                    }
                    State state;
                    switch (source.state()) {
                        case WaiterUtil.EVENT_SOURCE_MAPPING_STATE_ENABLED:
                            state = State.RUNNING;
                            break;
                        case WaiterUtil.EVENT_SOURCE_MAPPING_STATE_DISABLED:
                            state = State.PAUSED;
                            break;
                        case "Enabling":
                            state = State.STARTING;
                            break;
                        case "Disabling":
                            state = State.PAUSING;
                            break;
                        case "Creating":
                            state = State.CREATING;
                            break;
                        case "Updating":
                            state = State.UPDATING;
                            break;
                        case "Deleting":
                            return Stream.of();
                        default:
                            log.error("Retrieving event source mapping resulted in invalid state, customerId {} taskId {} source {} state original {} state now {}",
                                    customerId, taskId, source.uuid(), source.state(), source.state());
                            throw new InternalServerErrorException("Failed to determine current state, please try again later");
                    }
                    return Stream.of(new QueueSource(
                            queueNameOpt.get(),
                            source.uuid(),
                            state));
                })
                .collect(ImmutableSet.toImmutableSet());
        log.trace("Fetched customer {} task {} sources {}", customerId, taskId, queueSources);
        return queueSources;
    }

    private String getQueueStatementId(String queueName) {
        return QUEUE_STATEMENT_ID_PREFIX + queueName;
    }

    private Optional<String> getQueueNameFromStatementId(String statementId) {
        return statementId.startsWith(QUEUE_STATEMENT_ID_PREFIX)
                ? Optional.of(statementId.substring(QUEUE_STATEMENT_ID_PREFIX.length()))
                : Optional.empty();
    }
}
