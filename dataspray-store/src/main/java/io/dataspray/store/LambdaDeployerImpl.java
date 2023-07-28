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

package io.dataspray.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.primitives.Longs;
import com.google.gson.Gson;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.common.StringUtil;
import io.dataspray.store.util.WaiterUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ConflictException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetAliasRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsRequest;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PackageType;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.UpdateAliasRequest;
import software.amazon.awssdk.services.lambda.model.UpdateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.dataspray.common.DeployEnvironment.DEPLOY_ENVIRONMENT_PROP_NAME;
import static io.dataspray.runner.RawCoordinatorImpl.DATASPRAY_API_KEY_ENV;
import static io.dataspray.runner.RawCoordinatorImpl.DATASPRAY_CUSTOMER_ID_ENV;
import static java.util.function.Predicate.not;

@Slf4j
@ApplicationScoped
public class LambdaDeployerImpl implements LambdaDeployer {
    public static final int LAMBDA_DEFAULT_TIMEOUT = 900;
    /** Lambda version alias pointing to the active version in use */
    public static final String LAMBDA_ACTIVE_QUALIFIER = "ACTIVE";
    public static final String CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME_PROP_NAME = "deployer.customerPermissionBoundary.name";
    public static final String CUSTOMER_FUNCTION_POLICY_PATH_PREFIX = "Customer";
    public static final String CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LOGGING_PREFIX = CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "LambdaLogging";
    public static final String CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_SQS = CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "LambdaSqs";
    private static final long CODE_MAX_SIZE_IN_BYTES = 50 * 1024 * 1024;
    public static final String CODE_BUCKET_NAME_PROP_NAME = "deployer.codeBucketName";
    private static final String CODE_KEY_PREFIX = "customer/";
    public static final Function<DeployEnvironment, String> CUSTOMER_FUN_AND_ROLE_NAME_PREFIX_GETTER = deployEnv ->
            "ds" + deployEnv.getSuffix() + "-customer-";
    public static final Function<DeployEnvironment, String> FUN_NAME_WILDCARD_GETTER = deployEnv ->
            CUSTOMER_FUN_AND_ROLE_NAME_PREFIX_GETTER.apply(deployEnv) + "*";
    private static final String QUEUE_STATEMENT_ID_PREFIX = "customer-queue-statement-for-name-";

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = "aws.region")
    String awsRegion;
    @ConfigProperty(name = DEPLOY_ENVIRONMENT_PROP_NAME)
    DeployEnvironment deployEnv;
    @ConfigProperty(name = CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME_PROP_NAME)
    String customerFunctionPermissionBoundaryName;
    @ConfigProperty(name = CODE_BUCKET_NAME_PROP_NAME)
    String codeBucketName;

    @Inject
    IamClient iamClient;
    @Inject
    LambdaClient lambdaClient;
    @Inject
    S3Presigner s3Presigner;
    @Inject
    QueueStore queueStore;
    @Inject
    WaiterUtil waiterUtil;
    @Inject
    Gson gson;


    @Override
    public DeployedVersion deployVersion(String customerId, String customerApiKey, String taskId, String codeUrl, String handler, ImmutableSet<String> queueNames, Runtime runtime, boolean switchToImmediately) {
        String functionName = getFunctionName(customerId, taskId);

        // Check whether function exists
        Optional<FunctionConfiguration> existingFunctionOpt;
        try {
            existingFunctionOpt = Optional.of(lambdaClient.getFunction(GetFunctionRequest.builder()
                            .functionName(functionName)
                            .build())
                    .configuration());
        } catch (ResourceNotFoundException ex) {
            existingFunctionOpt = Optional.empty();
        }

        // Setup function IAM role
        String functionRoleName = getFunctionRoleName(customerId, taskId);
        String functionRoleArn = "arn:aws:iam::" + awsAccountId + ":role/" + functionRoleName;
        try {
            iamClient.getRole(GetRoleRequest.builder()
                    .roleName(functionRoleName)
                    .build());
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

        // Create or update function configuration and code
        final String publishedVersion;
        final String publishedDescription = generateVersionDescription(taskId, queueNames);
        Environment env = Environment.builder()
                .variables(Map.of(
                        DATASPRAY_API_KEY_ENV, customerApiKey,
                        DATASPRAY_CUSTOMER_ID_ENV, customerId)).build();
        if (existingFunctionOpt.isEmpty()) {
            // Create a new function with configuration and code all in one go
            publishedVersion = lambdaClient.createFunction(CreateFunctionRequest.builder()
                            .publish(true)
                            .functionName(functionName)
                            .description(publishedDescription)
                            .role(functionRoleArn)
                            .packageType(PackageType.ZIP)
                            .architectures(Architecture.ARM64)
                            .code(FunctionCode.builder()
                                    .s3Bucket(codeBucketName)
                                    .s3Key(getCodeKeyFromUrl(customerId, codeUrl))
                                    .build())
                            .runtime(runtime)
                            .handler(handler)
                            .environment(env)
                            .memorySize(128)
                            .timeout(LAMBDA_DEFAULT_TIMEOUT)
                            .build())
                    .version();

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
                    .revisionId(existingFunctionOpt.get().revisionId())
                    .build());
            // Wait until updated
            FunctionConfiguration a = waiterUtil.resolve(lambdaClient.waiter().waitUntilFunctionUpdatedV2(GetFunctionRequest.builder()
                            .functionName(functionName)
                            .build()))
                    .configuration();

            // Update function code
            publishedVersion = lambdaClient.updateFunctionCode(UpdateFunctionCodeRequest.builder()
                            .publish(true)
                            .functionName(functionName)
                            .architectures(Architecture.ARM64)
                            .s3Bucket(codeBucketName)
                            .s3Key(getCodeKeyFromUrl(customerId, codeUrl))
                            // updateFunctionConfiguration returns invalid revisionId
                            // https://github.com/aws/aws-sdk/issues/377
                            // .revisionId(updateFunctionRevisionId)
                            .build())
                    .version();
            // Wait until updated
            waiterUtil.resolve(lambdaClient.waiter().waitUntilFunctionUpdatedV2(GetFunctionRequest.builder()
                    .functionName(functionName)
                    .qualifier(publishedVersion)
                    .build()));
        }

        // Create active alias if doesn't exist
        boolean aliasAlreadyExists;
        try {
            lambdaClient.createAlias(CreateAliasRequest.builder()
                    .functionName(functionName)
                    .functionVersion(publishedVersion)
                    .name(LAMBDA_ACTIVE_QUALIFIER)
                    .build());
            aliasAlreadyExists = false;
        } catch (ResourceConflictException ex) {
            aliasAlreadyExists = true;
            log.trace("Lambda active tag already exists", ex);
        }

        // Add queue permissions on the deployed function version
        // Although this is not necessary, as the versioned function is never invoked directly,
        // rather the ACTIVE alias is invoked. However, this is a somewhat elegant way to keep
        // track of function version <--> queue names for later switchover/rollback/resume
        for (String inputQueueName : queueNames) {
            addQueuePermissionToFunction(customerId, taskId, publishedVersion, inputQueueName);
        }


        // In the next section, we need to create new sources disabled OR if we are switching to this version now
        // need to perform the entire switchover now. Firstly we will prepare the new queues, then perform the switchover
        // with the only difference with new queues will be we will create them already enabled.
        ImmutableSet<QueueSource> queueSources = getTaskQueueSources(customerId, taskId);
        Set<String> missingQueueSources = Sets.difference(queueNames, queueSources.stream().map(QueueSource::getQueueName).collect(Collectors.toSet()));

        // Prepare new queue sources, not creating just yet.
        for (String queueNameToAdd : missingQueueSources) {

            // Create queue if it doesn't exist
            if (!queueStore.queueExists(customerId, queueNameToAdd)) {
                queueStore.createQueue(customerId, queueNameToAdd);
            }

            // Add the permission for Event Source Mapping on the active qualifier
            addQueuePermissionToFunction(customerId, taskId, LAMBDA_ACTIVE_QUALIFIER, queueNameToAdd);

            // Lambda logging policy: get or create policy, then attach to role if needed
            String lambdaSqsPolicyName = CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_SQS + StringUtil.camelCase(functionName, true) + "Queue" + StringUtil.camelCase(queueNameToAdd, true);
            ensurePolicyAttachedToRole(functionRoleName, lambdaSqsPolicyName, gson.toJson(Map.of(
                    "Version", "2012-10-17",
                    "Statement", List.of(Map.of(
                            "Effect", "Allow",
                            "Action", List.of(
                                    "sqs:ReceiveMessage",
                                    "sqs:DeleteMessage",
                                    "sqs:GetQueueAttributes"),
                            "Resource", List.of(
                                    "arn:aws:sqs:" + awsRegion + ":" + awsAccountId + ":" + queueStore.getAwsQueueName(customerId, queueNameToAdd)))))));
        }

        if (switchToImmediately) {
            // Disable unneeded sources
            queueSources.stream()
                    .filter(source -> !queueNames.contains(source.getQueueName()))
                    .forEach(source -> disableSource(taskId, source, "switchover on deploy"));

            // Switch active tag
            // No need to switch if we just craeted it with out published version
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
                    .filter(source -> queueNames.contains(source.getQueueName()))
                    .forEach(source -> enableSource(taskId, source, "switchover on deploy"));
        }

        // Link SQS with Lambda
        for (String queueNameToAdd : missingQueueSources) {
            // Add the Event Source Mapping
            String sourceUuid = lambdaClient.createEventSourceMapping(CreateEventSourceMappingRequest.builder()
                            .functionName(functionName + ":" + LAMBDA_ACTIVE_QUALIFIER)
                            .enabled(switchToImmediately)
                            .batchSize(1)
                            .eventSourceArn("arn:aws:sqs:" + awsRegion + ":" + awsAccountId + ":" + queueStore.getAwsQueueName(customerId, queueNameToAdd))
                            .build())
                    .uuid();
        }

        return new DeployedVersion(
                publishedVersion,
                publishedDescription);
    }

    @Override
    public void switchVersion(String customerId, String taskId, String version) {
        // Gather info about the source and destination versions
        String functionName = getFunctionName(customerId, taskId);
        ImmutableSet<String> queueNames = readQueueNamesFromFunctionPermissions(customerId, taskId, version);
        ImmutableSet<QueueSource> queueSources = getTaskQueueSources(customerId, taskId);

        // Ensure all event sources are present before switchover
        Set<String> missingQueueSources = Sets.difference(queueNames, queueSources.stream().map(QueueSource::getQueueName).collect(Collectors.toSet()));
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
    public Versions getVersions(String customerId, String taskId) {
        String functionName = getFunctionName(customerId, taskId);

        // Find active version via alias
        Status activeStatus = status(customerId, taskId)
                .orElseThrow(() -> new NotFoundException("Task does not exist: " + taskId));

        // Fetch all versions
        ImmutableSet<DeployedVersion> versions = lambdaClient.listVersionsByFunctionPaginator(ListVersionsByFunctionRequest.builder()
                        .functionName(functionName)
                        .build())
                .stream()
                .map(ListVersionsByFunctionResponse::versions)
                .flatMap(Collection::stream)
                .filter(f -> !"$LATEST".equals(f.version()))
                .map(functionConfiguration -> new DeployedVersion(
                        functionConfiguration.version(),
                        functionConfiguration.description()))
                .collect(ImmutableSet.toImmutableSet());

        // Clean up old versions
        String activeVersionToKeep = activeStatus.getFunction().version();
        ImmutableSet<String> taskVersionsToDelete = versions.stream()
                .map(DeployedVersion::getVersion)
                .filter(not(activeVersionToKeep::equals))
                .map(Longs::tryParse)
                .filter(Objects::nonNull)
                .mapToLong(i -> i)
                .skip(2)
                .mapToObj(String::valueOf)
                .collect(ImmutableSet.toImmutableSet());
        taskVersionsToDelete.forEach(taskVersionToDelete -> lambdaClient.deleteFunction(DeleteFunctionRequest.builder()
                .functionName(functionName)
                .qualifier(taskVersionToDelete)
                .build()));

        return new Versions(
                activeStatus,
                versions.stream()
                        // Don't include the versions we just cleaned up
                        .filter(version -> !taskVersionsToDelete.contains(version.getVersion()))
                        .collect(ImmutableMap.toImmutableMap(
                                DeployedVersion::getVersion,
                                version -> version)));
    }

    @Override
    public Optional<Status> status(String customerId, String taskId) {
        Optional<String> activeVersionOpt = fetchActiveVersion(customerId, taskId);
        if (activeVersionOpt.isEmpty()) {
            return Optional.empty();
        }

        FunctionConfiguration function;
        try {
            function = lambdaClient.getFunction(GetFunctionRequest.builder()
                            .functionName(getFunctionName(customerId, taskId))
                            .qualifier(activeVersionOpt.get())
                            .build())
                    .configuration();
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }

        ImmutableSet<String> queueNames = readQueueNamesFromFunctionPermissions(customerId, taskId, activeVersionOpt.get());
        final State state;
        if (queueNames.isEmpty()) {
            state = State.RUNNING;
        } else {
            ImmutableMap<String, QueueSource> queueSources = getTaskQueueSources(customerId, taskId).stream()
                    .collect(ImmutableMap.toImmutableMap(QueueSource::getQueueName, s -> s));

            state = queueNames.stream()
                    .map(queueName -> Optional.ofNullable(queueSources.get(queueName))
                            .map(QueueSource::getState)
                            .orElse(State.PAUSED))
                    .max(Comparator.comparing(State::getWeight))
                    .orElse(State.RUNNING);
        }

        return Optional.of(new Status(taskId, function, state));
    }

    @Override
    public ImmutableList<Status> statusAll(String customerId) {
        // TODO this doesn't scale well, need to start storing a list of tasks in a database
        return lambdaClient.listFunctionsPaginator(ListFunctionsRequest.builder()
                        .build()).stream()
                .map(ListFunctionsResponse::functions)
                .flatMap(Collection::stream)
                .map(FunctionConfiguration::functionName)
                .map(functionName -> getTaskIdFromFunctionName(customerId, functionName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .map(taskId -> status(customerId, taskId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public void pause(String customerId, String taskId) {
        //  Disable all queue sources
        getTaskQueueSources(customerId, taskId)
                .forEach(source -> disableSource(taskId, source, "pause"));
    }

    @Override
    public void resume(String customerId, String taskId) {
        String activeVersion = fetchActiveVersion(customerId, taskId).orElseThrow(() -> new BadRequestException("Task not active"));
        ImmutableSet<String> queueNames = readQueueNamesFromFunctionPermissions(customerId, taskId, activeVersion);
        ImmutableSet<QueueSource> queueSources = getTaskQueueSources(customerId, taskId);

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
    public void delete(String customerId, String taskId) {
        lambdaClient.deleteFunction(DeleteFunctionRequest.builder()
                .functionName(getFunctionName(customerId, taskId))
                .build());
    }

    @Override
    public UploadCodeClaim uploadCode(String customerId, String taskId, long contentLengthBytes) {
        if (contentLengthBytes > CODE_MAX_SIZE_IN_BYTES) {
            throw new BadRequestException("Maximum code size is " + CODE_MAX_SIZE_IN_BYTES / 1024 / 1024 + "MB, please contact support");
        }
        String key = getCodeKeyPrefix(customerId)
                     + taskId
                     + "-"
                     + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC).format(Instant.now()) + ".zip";
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

    private String getFunctionName(String customerId, String taskId) {
        return getFunctionPrefix(customerId) + taskId;
    }

    private String getFunctionRoleName(String customerId, String taskId) {
        return getFunctionName(customerId, taskId) + "-role";
    }

    private String getFunctionPrefix(String customerId) {
        return CUSTOMER_FUN_AND_ROLE_NAME_PREFIX_GETTER.apply(deployEnv) + customerId + "-";
    }

    private Optional<String> getTaskIdFromFunctionName(String customerId, String functionName) {
        String functionPrefix = getFunctionPrefix(customerId);
        return functionName.startsWith(functionPrefix)
                ? Optional.of(functionName.substring(functionPrefix.length()))
                : Optional.empty();
    }

    private String generateVersionDescription(String taskId, ImmutableSet<String> inputQueueNames) {
        return "Task " + taskId + " with inputs [" + inputQueueNames.stream().collect(Collectors.joining(", ")) + "] deployed on " + Instant.now();
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
        } catch (NoSuchEntityException ex) {
            iamClient.putRolePolicy(PutRolePolicyRequest.builder()
                    .roleName(roleName)
                    .policyName(policyName)
                    .policyDocument(policyDocument)
                    .build());
            waiterUtil.resolve(waiterUtil.waitUntilPolicyAttachedToRole(roleName, policyName));
        }
    }

    private Optional<String> fetchActiveVersion(String customerId, String taskId) {
        // Find active version via alias
        try {
            return Optional.of(lambdaClient.getAlias(GetAliasRequest.builder()
                            .functionName(getFunctionName(customerId, taskId))
                            .name(LAMBDA_ACTIVE_QUALIFIER)
                            .build())
                    .functionVersion());
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }
    }

    private void addQueuePermissionToFunction(String customerId, String taskId, String qualifier, String queueName) {
        try {
            lambdaClient.addPermission(AddPermissionRequest.builder()
                    .functionName(getFunctionName(customerId, taskId) + ":" + qualifier)
                    .statementId(getQueueStatementId(queueName))
                    .principal("sqs.amazonaws.com")
                    .action("lambda:InvokeFunction")
                    .sourceArn("arn:aws:sqs:" + awsRegion + ":" + awsAccountId + ":" + queueStore.getAwsQueueName(customerId, queueName))
                    .qualifier(qualifier)
                    .build());
        } catch (ResourceConflictException ex) {
            log.warn("Lambda permission for queue already exists, ignoring", ex);
        }
    }

    private ImmutableSet<String> readQueueNamesFromFunctionPermissions(String customerId, String taskId, String version) {
        ResourcePolicyDocument resourcePolicyDocument = gson.fromJson(lambdaClient.getPolicy(software.amazon.awssdk.services.lambda.model.GetPolicyRequest.builder()
                        .functionName(getFunctionName(customerId, taskId) + ":" + version)
                        .qualifier(version)
                        .build())
                .policy(), ResourcePolicyDocument.class);
        if (!"2012-10-17".equals(resourcePolicyDocument.getVersion())) {
            log.error("Cannot parse resource policy document with unknown version {} for customer {} taskId {} version {}",
                    resourcePolicyDocument.getVersion(), customerId, taskId, version);
            throw new InternalServerErrorException("Cannot parse resource, please contact support");
        }
        ImmutableSet<String> queueNames = resourcePolicyDocument
                .getStatements().stream()
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
                        .functionName(getFunctionName(customerId, taskId) + ":" + LAMBDA_ACTIVE_QUALIFIER)
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
                    Optional<String> queueNameOpt = queueStore.extractQueueNameFromAwsQueueName(customerId, awsQueueName);
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
