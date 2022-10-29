package io.dataspray.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
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
            Runtime runtime,
            boolean switchToImmediately);

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
        State state;
    }

    @Getter
    @AllArgsConstructor
    enum State {
        RUNNING(0, false, Optional.of(true)),
        STARTING(1, true, Optional.of(true)),
        PAUSED(2, false, Optional.of(false)),
        PAUSING(3, true, Optional.of(false)),
        UPDATING(4, true, Optional.empty()),
        CREATING(5, true, Optional.empty());
        /**
         * When determining the state of a group of items,
         * the state with the highest weight should be assigned to the whole group.
         */
        private final int weight;
        private final boolean isUpdating;
        /** Empty indicates we don't know, it's either Updating or Creating without knowing the final state */
        private final Optional<Boolean> isFinalStateRunningOpt;
    }

    @Value
    class QueueSource {
        String queueName;
        String uuid;
        State state;
    }

    @Value
    class Versions {
        Status status;
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
