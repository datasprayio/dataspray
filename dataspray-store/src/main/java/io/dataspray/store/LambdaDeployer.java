package io.dataspray.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.SerializedName;
import lombok.Value;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.Runtime;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

public interface LambdaDeployer {

    DeployedVersion deployVersion(
            String customerId,
            String taskId,
            String codeUrl,
            String handler,
            ImmutableSet<String> inputQueueNames,
            Runtime runtime);

    void switchVersion(String customerId, String taskId, String version);

    Versions getVersions(String customerId, String taskId);

    Optional<Status> status(String customerId, String taskId);

    ImmutableList<Status> statusAll(String customerId);

    void pause(String customerId, String taskId);

    void resume(String customerId, String taskId);

    void delete(String customerId, String taskId);

    UploadCodeClaim uploadCode(String customerId, String taskId, long contentLengthBytes);

    @Value
    class Status {
        String taskId;
        FunctionConfiguration function;
        boolean active;
    }

    @Value
    class QueueSource {
        String queueName;
        String uuid;
        boolean enabled;
    }

    @Value
    class Versions {
        Optional<String> active;
        ImmutableMap<String, DeployedVersion> taskByVersion;
    }

    @Value
    class DeployedVersion {
        String version;
        String description;
    }

    @Value
    class ResourcePolicyDocument {
        @Nonnull
        @SerializedName("Version")
        String version;

        @Nonnull
        @SerializedName("Statement")
        List<ResourcePolicyStatement> statements;
    }

    @Value
    class ResourcePolicyStatement {
        @Nonnull
        @SerializedName("Sid")
        String statementId;
    }

    @Value
    class UploadCodeClaim {
        @Nonnull
        String presignedUrl;

        @Nonnull
        String codeUrl;
    }
}
