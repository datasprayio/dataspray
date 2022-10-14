package io.dataspray.stream.control;

import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import io.dataspray.common.StringUtil;
import io.dataspray.lambda.resource.AbstractResource;
import io.dataspray.store.QueueStore;
import io.dataspray.stream.control.model.DeployRequest;
import io.dataspray.stream.control.model.TaskStatus;
import io.dataspray.stream.control.model.TaskStatus.StatusEnum;
import io.dataspray.stream.control.model.TaskStatuses;
import io.dataspray.stream.control.model.UpdateRequest;
import io.dataspray.stream.control.model.UploadCodeRequest;
import io.dataspray.stream.control.model.UploadCodeResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreatePolicyVersionRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;
import software.amazon.awssdk.services.iam.model.Policy;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iam.model.SetDefaultPolicyVersionRequest;
import software.amazon.awssdk.services.iam.waiters.IamWaiter;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.Architecture;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteAliasRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PackageType;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.dataspray.stream.control.deploy.ControlStack.*;

@Slf4j
@ApplicationScoped
public class ControlResource extends AbstractResource implements ControlApi {
    private static final int CODE_MAX_CONCURRENCY = 100;
    private static final long CODE_MAX_SIZE_IN_BYTES = 50 * 1024 * 1024;
    public static final String CODE_BUCKET_NAME = "io-dataspray-code-upload";
    private static final String CODE_KEY_PREFIX = "customer/";
    public static final String FUN_NAME_PREFIX = "customer-";
    public static final String FUN_NAME_WILDCARD = FUN_NAME_PREFIX + "*";

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = "aws.region")
    String awsRegion;

    @Inject
    IamClient iamClient;
    @Inject
    LambdaClient lambdaClient;
    @Inject
    S3Presigner s3Presigner;
    @Inject
    QueueStore queueStore;
    @Inject
    Gson gson;

    // TODO get customer and setup billing
    private final String customerId = "matus";

    @Override
    @SneakyThrows
    public TaskStatus deploy(DeployRequest deployRequest) {
        Runtime runtime = Enums.getIfPresent(Runtime.class, deployRequest.getRuntime().name()).toJavaUtil()
                .orElseThrow(() -> new RuntimeException("Unknown runtime"));
        String functionName = getFunctionName(deployRequest.getTaskId());
        String functionRoleName = getFunctionRoleName(deployRequest.getTaskId());
        Optional<FunctionConfiguration> activeFunctionVersion = getActiveFunctionVersion(functionName);

        // Create queues that don't exist
        for (String inputQueueName : deployRequest.getInputQueueNames()) {
            if (queueStore.queueAttributes(customerId, inputQueueName).isEmpty()) {
                queueStore.createQueue(customerId, inputQueueName);
            }
        }

        // Fetch or create role
        Role functionRole;
        try {
            functionRole = iamClient.getRole(GetRoleRequest.builder()
                            .roleName(functionRoleName)
                            .build())
                    .role();
        } catch (NoSuchEntityException ex) {
            CreateRoleResponse createRoleResponse = iamClient.createRole(CreateRoleRequest.builder()
                    .roleName(functionRoleName)
                    .description("Auto-created for Lambda " + functionName)
                    .permissionsBoundary("arn:aws:iam::" + awsAccountId + ":policy/" + CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME)
                    .assumeRolePolicyDocument(gson.toJson(Map.of(
                            "Version", "2012-10-17",
                            "Statement", List.of(Map.of(
                                    "Effect", "Allow",
                                    "Action", List.of("sts:AssumeRole"),
                                    "Principal", Map.of(
                                            "Service", List.of("lambda.amazonaws.com")))))))
                    .build());
            functionRole = iamWaiter(waiter -> waiter.waitUntilRoleExists(GetRoleRequest.builder()
                    .roleName(createRoleResponse.role().roleName())
                    .build()))
                    .role();
        }

        // Lambda logging policy: get or create policy, then attach to role if needed
        String customerLoggingPolicyName = CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LOGGING_PREFIX + "Customer" + StringUtil.camelCase(functionName, true);
        try {
            // First see if policy is attached to the role already
            iamClient.getRolePolicy(GetRolePolicyRequest.builder()
                    .roleName(functionRole.roleName())
                    .policyName(customerLoggingPolicyName)
                    .build());
        } catch (NoSuchEntityException ex) {
            Policy customerLoggingPolicy;
            try {
                // It's not attached, let's see if it exists
                customerLoggingPolicy = iamClient.getPolicy(GetPolicyRequest.builder()
                                .policyArn("arn:aws:iam::" + awsAccountId + ":policy/" + customerLoggingPolicyName)
                                .build())
                        .policy();
            } catch (NoSuchEntityException ex2) {
                // It doesn't exist, create it first
                CreatePolicyResponse createPolicyResponse = iamClient.createPolicy(CreatePolicyRequest.builder()
                        .policyName(customerLoggingPolicyName)
                        .policyDocument(gson.toJson(Map.of(
                                "Version", "2012-10-17",
                                "Statement", List.of(Map.of(
                                        "Effect", "Allow",
                                        "Action", List.of(
                                                "logs:CreateLogGroup",
                                                "logs:CreateLogStream",
                                                "logs:PutLogEvents"),
                                        "Resource", List.of(
                                                "arn:aws:logs:" + awsRegion + ":" + awsAccountId + ":log-group:/aws/lambda/" + FUN_NAME_WILDCARD,
                                                "arn:aws:logs:" + awsRegion + ":" + awsAccountId + ":log-group:/aws/lambda/" + FUN_NAME_WILDCARD + ":*"
                                        ))))))
                        .build());
                customerLoggingPolicy = iamWaiter(waiter -> waiter.waitUntilPolicyExists(GetPolicyRequest.builder()
                        .policyArn(createPolicyResponse.policy().arn())
                        .build()))
                        .policy();
            }
            // Now attach the policy to the role
            AttachRolePolicyResponse attachRolePolicyResponse = iamClient.attachRolePolicy(AttachRolePolicyRequest.builder()
                    .roleName(functionRole.roleName())
                    .policyArn(customerLoggingPolicy.arn())
                    .build());
        }

        // Update code, either to existing function or create a new one
        final String updatedCodeRevisionId;
        if (activeFunctionVersion.isEmpty()) {
            // If function doesn't exist, create it without publishing yet
            updatedCodeRevisionId = lambdaClient.createFunction(CreateFunctionRequest.builder()
                            .publish(false)
                            .functionName(functionName)
                            .role(functionRoleName)
                            .packageType(PackageType.ZIP)
                            .architectures(Architecture.X86_64)
                            .code(FunctionCode.builder()
                                    .s3Bucket(CODE_BUCKET_NAME)
                                    .s3Key(getCodeKeyFromUrl(deployRequest.getCodeUrl()))
                                    .build())
                            .runtime(runtime)
                            .memorySize(128)
                            .timeout(900)
                            .build())
                    .revisionId();
        } else {
            // Update function code without publishing yet
            updatedCodeRevisionId = lambdaClient.updateFunctionCode(UpdateFunctionCodeRequest.builder()
                            .publish(false)
                            .functionName(functionName)
                            .architectures(Architecture.X86_64)
                            .s3Bucket(CODE_BUCKET_NAME)
                            .s3Key(getCodeKeyFromUrl(deployRequest.getCodeUrl()))
                            // Make sure no one is updating code concurrently
                            .revisionId(activeFunctionVersion.get().revisionId())
                            .build())
                    .revisionId();
        }

        // Lambda sqs policy update:
        // - Find or create the policy
        // - Update version without setting it as default just yet
        // - Ensure it's attached to role
        String policyDocument = gson.toJson(Map.of(
                "Version", "2012-10-17",
                "Statement", List.of(Map.of(
                        "Effect", "Allow",
                        "Action", List.of(
                                "sqs:ReceiveMessage",
                                "sqs:DeleteMessage"),
                        "Resource", deployRequest.getInputQueueNames().stream()
                                .map(queueName -> "arn:aws:sqs:" + awsRegion + ":" + awsAccountId + ":" + queueStore.getAwsQueueName(customerId, queueName))
                                .collect(Collectors.toList())))));
        String customerSqsPolicyName = CUSTOMER_FUNCTION_PERMISSION_CUSTOMEMR_LAMBDA_SQS + "Customer" + StringUtil.camelCase(functionName, true);
        Policy customerSqsPolicy;
        Optional<String> customerSqsPolicyPreviousVersionOpt = Optional.empty();
        Optional<String> customerSqsPolicyUpcomingVersionOpt = Optional.empty();
        try {
            // Find it first
            customerSqsPolicy = iamClient.getPolicy(GetPolicyRequest.builder()
                            .policyArn("arn:aws:iam::" + awsAccountId + ":policy/" + customerSqsPolicyName)
                            .build())
                    .policy();
            // Keep track of previous version in case of rollback
            customerSqsPolicyPreviousVersionOpt = Optional.of(customerSqsPolicy.defaultVersionId());
            // Create new version of policy but don't set it as default just yet
            customerSqsPolicyUpcomingVersionOpt = Optional.of(iamClient.createPolicyVersion(CreatePolicyVersionRequest.builder()
                            .setAsDefault(false)
                            .policyArn(customerSqsPolicy.arn())
                            .policyDocument(policyDocument)
                            .build())
                    .policyVersion()
                    .versionId());
        } catch (NoSuchEntityException ex) {
            // It doesn't exist, create it first
            CreatePolicyResponse createPolicyResponse = iamClient.createPolicy(CreatePolicyRequest.builder()
                    .policyName(customerSqsPolicyName)
                    .policyDocument(policyDocument)
                    .build());
            customerSqsPolicy = iamWaiter(waiter -> waiter.waitUntilPolicyExists(GetPolicyRequest.builder()
                    .policyArn(createPolicyResponse.policy().arn())
                    .build()))
                    .policy();
        }

        // Pause previous function version
        if (activeFunctionVersion.isPresent()) {
            pauseFunction(functionName);
        }

        // Now is a critical time, we paused the current execution and we need to:
        // - Switch out the policy version
        // - Switch out the function code version

        // TODO TODO TODO TODO

        // Switch to the new default policy if not already created as default
        if (customerSqsPolicyUpcomingVersionOpt.isPresent()) {
            iamClient.setDefaultPolicyVersion(SetDefaultPolicyVersionRequest.builder()
                    .policyArn(customerSqsPolicy.arn())
                    .versionId(customerSqsPolicyUpcomingVersionOpt.get())
                    .build());
        }

        // Deploy and publish new function
        lambdaClient.ver(PublishVersionRequest.builder()
                .functionName(functionName)
                // Make sure no one is publish function concurrently
                .revisionId(updatedFunction.revisionId())
                .build());

        lambdaClient.deleteFunctionEventInvokeConfig()
        lambdaClient.createEventSourceMapping(CreateEventSourceMappingRequest.builder()
                ..build());

        // TODO below

        if (activeFunctionVersion.isPresent()) {
            // Pause previous function version
            pauseFunction(functionName);

            // TODO update role
            iamClient.createPolicyVersion();
            try {

                // Deploy and publish new function
                lambdaClient.publishVersion(PublishVersionRequest.builder()
                        .functionName(functionName)
                        // Make sure no one is publish function concurrently
                        .revisionId(updatedFunction.revisionId())
                        .build());

            } catch () {

            }
        }

        // TODO delete unused roles

        lambdaClient.putFunctionConcurrency(PutFunctionConcurrencyRequest.builder()
                .functionName(functionName)
                .reservedConcurrentExecutions(CODE_MAX_CONCURRENCY).build());
        return status(deployRequest.getTaskId());
    }

    @Override
    public TaskStatus status(String taskId) {
        GetFunctionResponse functionResponse;
        GetFunctionConcurrencyResponse concurrencyResponse;
        try {
            functionResponse = lambdaClient.getFunction(GetFunctionRequest.builder()
                    .functionName(getFunctionName(taskId))
                    .build());
            concurrencyResponse = lambdaClient.getFunctionConcurrency(GetFunctionConcurrencyRequest.builder()
                    .functionName(getFunctionName(taskId))
                    .build());
        } catch (ResourceNotFoundException ex) {
            return getStatusMissing(taskId);
        }
        return getStatus(functionResponse.configuration(), Optional.ofNullable(concurrencyResponse.reservedConcurrentExecutions()));
    }

    @Override
    public TaskStatuses statusAll() {
        String functionPrefix = getFunctionPrefix();
        ImmutableList.Builder<TaskStatus> statusesBuilder = ImmutableList.builder();
        Optional<String> markerOpt = Optional.empty();
        // TODO this doesn't scale well, need to start storing a list of tasks in a database
        do {
            ListFunctionsResponse response = lambdaClient.listFunctions(ListFunctionsRequest.builder()
                    .marker(markerOpt.orElse(null))
                    .maxItems(50)
                    .build());
            for (FunctionConfiguration function : response.functions()) {
                if (function.functionName().startsWith(functionPrefix)) {
                    GetFunctionConcurrencyResponse concurrencyResponse = lambdaClient.getFunctionConcurrency(GetFunctionConcurrencyRequest.builder()
                            .functionName(function.functionName())
                            .build());
                    statusesBuilder.add(getStatus(function, Optional.ofNullable(concurrencyResponse.reservedConcurrentExecutions())));
                }
            }
            markerOpt = Optional.ofNullable(Strings.emptyToNull(response.nextMarker()));
        } while (markerOpt.isPresent());
        return new TaskStatuses(statusesBuilder.build());
    }

    @Override
    public TaskStatus pause(String taskId) {
        pauseFunction(getFunctionName(taskId));
        return status(taskId);
    }

    @Override
    public TaskStatus resume(String taskId) {
        resumeFunction(getFunctionName(taskId));
        return status(taskId);
    }

    private PutFunctionConcurrencyResponse pauseFunction(String functionName) {
        return setFunctionConcurrency(functionName, 0);
    }

    private PutFunctionConcurrencyResponse resumeFunction(String functionName) {
        return setFunctionConcurrency(functionName, CODE_MAX_CONCURRENCY);
    }

    private PutFunctionConcurrencyResponse setFunctionConcurrency(String functionName, int concurrency) {
        return lambdaClient.putFunctionConcurrency(PutFunctionConcurrencyRequest.builder()
                .functionName(functionName)
                .reservedConcurrentExecutions(concurrency)
                .build());
    }

    @Override
    public TaskStatus delete(String taskId) {
        lambdaClient.deleteFunction(DeleteFunctionRequest.builder()
                .functionName(getFunctionName(taskId))
                .build());
        return getStatusMissing(taskId);
    }

    @Override
    public TaskStatus update(String taskId, UpdateRequest updateRequest) {
        if (!Strings.isNullOrEmpty(updateRequest.getCodeUrl())) {
            lambdaClient.updateFunctionCode(UpdateFunctionCodeRequest.builder()
                    .functionName(getFunctionName(taskId))
                    .s3Bucket(CODE_BUCKET_NAME)
                    .s3Key(getCodeKeyFromUrl(updateRequest.getCodeUrl()))
                    .build());
        }
        return status(taskId);
    }

    @Override
    public UploadCodeResponse uploadCode(UploadCodeRequest uploadCodeRequest) {
        if (uploadCodeRequest.getContentLengthBytes() > CODE_MAX_SIZE_IN_BYTES) {
            throw new RuntimeException("Maximum code size is " + CODE_MAX_SIZE_IN_BYTES / 1024 / 1024 + "MB, please contact support");
        }
        String key = getCodeKeyPrefix()
                + uploadCodeRequest.getTaskId()
                + "-"
                + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC).format(Instant.now()) + ".zip";
        String codeUrl = "s3://" + CODE_BUCKET_NAME + "/" + key;
        String presignedUrl = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(CODE_BUCKET_NAME)
                                .key(key)
                                .contentLength(uploadCodeRequest.getContentLengthBytes())
                                .contentType("application/zip")
                                .build())
                        .signatureDuration(Duration.ofHours(1))
                        .build())
                .url()
                .toExternalForm();
        return new UploadCodeResponse(presignedUrl, codeUrl);
    }

    private String getFunctionName(String taskId) {
        return getFunctionPrefix() + taskId;
    }

    private String getFunctionRoleName(String taskId) {
        return getFunctionName(taskId) + "-role";
    }

    private String getFunctionPrefix() {
        return FUN_NAME_PREFIX + customerId + "-";
    }

    private String getCodeKeyPrefix() {
        return CODE_KEY_PREFIX + customerId + "/";
    }

    private String getCodeKeyFromUrl(String url) {
        String s3UrlAndBucketPrefix = "s3://" + CODE_BUCKET_NAME + "/";
        String s3UrlAndBucketAndKeyPrefix = s3UrlAndBucketPrefix + getCodeKeyPrefix();
        if (!url.startsWith(s3UrlAndBucketAndKeyPrefix)) {
            throw new RuntimeException("Incorrect bucket location");
        }
        return url.substring(s3UrlAndBucketPrefix.length());
    }

    private TaskStatus getStatus(FunctionConfiguration function, Optional<Integer> reservedConcurrencyOpt) {
        return new TaskStatus(
                function.functionName().substring(getFunctionPrefix().length()),
                (reservedConcurrencyOpt.isEmpty() || reservedConcurrencyOpt.get() > 0) ? StatusEnum.RUNNING : StatusEnum.PAUSED,
                Enums.getIfPresent(TaskStatus.LastUpdateStatusEnum.class, function.lastUpdateStatus().name())
                        .toJavaUtil().orElse(null),
                function.lastUpdateStatusReason());
    }

    private TaskStatus getStatusMissing(String taskId) {
        return TaskStatus.builder()
                .taskId(taskId)
                .status(StatusEnum.MISSING)
                .build();
    }

    /**
     * Logic to fetch current state of function and attempt to reconcile any misdeployments. Assumptions:
     * <ul>
     *   <li>Each function can have exactly one published version</li>
     *   <li>Function with multiple versions will have older version deleted</li>
     *   <li>Function with no versions will be completely deleted</li>
     * </ul>
     */
    private Optional<FunctionConfiguration> getActiveFunctionVersion(String functionName) {
        Map<Long, FunctionConfiguration> activeVersions = Maps.newHashMap();
        Set<FunctionConfiguration> latestVersions = lambdaClient.listVersionsByFunctionPaginator(ListVersionsByFunctionRequest.builder()
                        .functionName(functionName)
                        .build())
                .stream()
                .map(ListVersionsByFunctionResponse::versions)
                .flatMap(Collection::stream)
                .filter(f -> {
                    if ("$LATEST".equals(f.version())) {
                        return true;
                    } else {
                        long version;
                        try {
                            version = Long.parseLong(f.version());
                        } catch (NumberFormatException ex) {
                            log.error("Unexpected non-numeric function version '{}' for function {}",
                                    f.version(), functionName);
                            throw ex;
                        }
                        activeVersions.put(version, f);
                        return false;
                    }
                })
                .collect(Collectors.toSet());

        // Get active version
        final Optional<FunctionConfiguration> activeFunction;
        if (activeVersions.size() > 1) {
            long activeVersion = activeVersions.keySet().stream()
                    .max(Long::compareTo)
                    .get();
            log.warn("Found more than one active version, deleting others from function {}: keeping version {} from all active {}",
                    functionName, latestVersions, activeVersions);
            activeVersions.entrySet().stream()
                    .filter(e -> e.getKey() != activeVersion)
                    .map(Map.Entry::getValue)
                    .map(FunctionConfiguration::version)
                    .forEach(versionToDelete -> lambdaClient.deleteAlias(DeleteAliasRequest.builder()
                            .functionName(functionName)
                            .name(versionToDelete)
                            .build()));
            activeFunction = Optional.ofNullable(activeVersions.get(activeVersion));
        } else if (activeVersions.size() == 1) {
            activeFunction = Optional.ofNullable(activeVersions.values().iterator().next());
        } else {
            activeFunction = Optional.empty();
        }

        // Get latest un-versioned function
        final Optional<FunctionConfiguration> latestFunction;
        if (latestVersions.size() > 1) {
            log.error("Found more than one $LATEST version iterating function {}: latest {} active {}",
                    functionName, latestVersions, activeVersions);
            throw new RuntimeException("Failed to deploy");
        } else if (latestVersions.size() == 1) {
            latestFunction = Optional.of(latestVersions.iterator().next());
        } else {
            latestFunction = Optional.empty();
        }

        if (activeFunction.isPresent()) {
            if (latestFunction.isEmpty()) {
                log.warn("Function has a version but no latest {}: latest {} active {}", functionName, latestVersions, activeVersions);
            }
            return activeFunction;
        } else if (latestFunction.isPresent()) {
            // Remove function if no active versions
            log.warn("Deleting function with no versions published {}: latest {} active {}",
                    functionName, latestVersions, activeVersions);
            lambdaClient.deleteFunction(DeleteFunctionRequest.builder()
                    .functionName(functionName).build());
            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    private <R> R iamWaiter(Function<IamWaiter, WaiterResponse<R>> waiterCall) {
        ResponseOrException<R> response = waiterCall.apply(iamClient.waiter())
                .matched();
        return response.response()
                .orElseThrow(() -> response
                        .exception().map(RuntimeException::new)
                        .orElseGet(RuntimeException::new));
    }
}
