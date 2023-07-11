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
            String customerApiKey,
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
