package io.dataspray.stream.control;

import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.dataspray.lambda.resource.AbstractResource;
import io.dataspray.stream.server.ControlApi;
import io.dataspray.stream.server.model.DeployRequest;
import io.dataspray.stream.server.model.TaskStatus;
import io.dataspray.stream.server.model.TaskStatus.StatusEnum;
import io.dataspray.stream.server.model.TaskStatuses;
import io.dataspray.stream.server.model.UpdateRequest;
import io.dataspray.stream.server.model.UploadCodeRequest;
import io.dataspray.stream.server.model.UploadCodeResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.Architecture;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.PackageType;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@Path("/api")
public class ControlResource extends AbstractResource implements ControlApi {
    private static final int CODE_MAX_CONCURRENCY = 100;
    private static final long CODE_MAX_SIZE_IN_BYTES = 50 * 1024 * 1024;
    private static final String CODE_BUCKET = "io.dataspray.code";
    private static final String CODE_KEY_PREFIX = "user/";
    private static final String FUN_NAME_PREFIX = "user-";

    // TODO get customer and setup billing
    private final String customerId = "matus";

    @Override
    @SneakyThrows
    public TaskStatus deploy(DeployRequest deployRequest) {
        Runtime runtime = Enums.getIfPresent(Runtime.class, deployRequest.getRuntime().name()).toJavaUtil()
                .orElseThrow(() -> new RuntimeException("Unknown runtime"));
        String functionName = getFunctionName(deployRequest.getTaskId());
        CreateFunctionResponse function = LambdaClient.create().createFunction(CreateFunctionRequest.builder()
                .functionName(functionName)
                .packageType(PackageType.ZIP)
                .architectures(Architecture.X86_64)
                .code(FunctionCode.builder()
                        .s3Bucket(CODE_BUCKET)
                        .s3Key(getCodeKeyFromUrl(deployRequest.getCodeUrl()))
                        .build())
                .runtime(runtime)
                .memorySize(128)
                .build());
        LambdaClient.create().putFunctionConcurrency(PutFunctionConcurrencyRequest.builder()
                .functionName(functionName)
                .reservedConcurrentExecutions(CODE_MAX_CONCURRENCY).build());
        return status(deployRequest.getTaskId());
    }

    @Override
    public TaskStatus status(String taskId) {
        GetFunctionResponse functionResponse;
        GetFunctionConcurrencyResponse concurrencyResponse;
        try {
            functionResponse = LambdaClient.create().getFunction(GetFunctionRequest.builder()
                    .functionName(getFunctionName(taskId))
                    .build());
            concurrencyResponse = LambdaClient.create().getFunctionConcurrency(GetFunctionConcurrencyRequest.builder()
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
            ListFunctionsResponse response = LambdaClient.create().listFunctions(ListFunctionsRequest.builder()
                    .marker(markerOpt.orElse(null))
                    .maxItems(50)
                    .build());
            for (FunctionConfiguration function : response.functions()) {
                if (function.functionName().startsWith(functionPrefix)) {
                    GetFunctionConcurrencyResponse concurrencyResponse = LambdaClient.create().getFunctionConcurrency(GetFunctionConcurrencyRequest.builder()
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
        LambdaClient.create().putFunctionConcurrency(PutFunctionConcurrencyRequest.builder()
                .functionName(getFunctionName(taskId))
                .reservedConcurrentExecutions(0)
                .build());
        return status(taskId);
    }

    @Override
    public TaskStatus resume(String taskId) {
        LambdaClient.create().putFunctionConcurrency(PutFunctionConcurrencyRequest.builder()
                .functionName(getFunctionName(taskId))
                .reservedConcurrentExecutions(CODE_MAX_CONCURRENCY)
                .build());
        return status(taskId);
    }

    @Override
    public TaskStatus delete(String taskId) {
        LambdaClient.create().deleteFunction(DeleteFunctionRequest.builder()
                .functionName(getFunctionName(taskId))
                .build());
        return getStatusMissing(taskId);
    }

    @Override
    public TaskStatus update(String taskId, UpdateRequest updateRequest) {
        if (!Strings.isNullOrEmpty(updateRequest.getCodeUrl())) {
            UpdateFunctionCodeResponse response = LambdaClient.create().updateFunctionCode(UpdateFunctionCodeRequest.builder()
                    .functionName(getFunctionName(taskId))
                    .s3Bucket(CODE_BUCKET)
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
                + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").format(Instant.now()) + ".zip";
        String codeUrl = "s3://" + CODE_BUCKET + "/" + key;
        String presignedUrl = S3Presigner.create().presignPutObject(PutObjectPresignRequest.builder()
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(CODE_BUCKET)
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

    private String getFunctionPrefix() {
        return FUN_NAME_PREFIX + customerId + "-";
    }

    private String getCodeKeyPrefix() {
        return CODE_KEY_PREFIX + customerId + "/";
    }

    private String getCodeKeyFromUrl(String url) {
        String s3UrlAndBucketPrefix = "s3://" + CODE_BUCKET + "/";
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
        return new TaskStatus(
                taskId,
                StatusEnum.MISSING,
                null,
                null);
    }
}
