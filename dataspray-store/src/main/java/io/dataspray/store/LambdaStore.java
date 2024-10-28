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

package io.dataspray.store;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.DynamoTable;
import io.dataspray.singletable.ShardPageResult;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.ConflictException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;
import static io.dataspray.store.util.CycleUtil.Node;

public interface LambdaStore {

    LambdaRecord set(String organizationName,
                     String taskId,
                     String username,
                     ImmutableSet<String> inputQueueNames,
                     ImmutableSet<String> outputQueueNames,
                     Optional<String> endpointUrlOpt);

    Optional<LambdaRecord> get(String organizationName, String taskId);

    void getForOrganization(String organizationName, boolean includeDeleted, Consumer<ImmutableList<LambdaRecord>> batchConsumer);

    ShardPageResult<LambdaRecord> getForOrganization(String organizationName, boolean includeDeleted, Optional<String> cursorOpt);

    LambdaRecord markDeleted(String organizationName, String taskId);

    /**
     * Checks whether a new or updated Task would cause a cycle in the existing graph or functions.
     */
    Optional<List<Node>> checkLoops(String organizationName,
                                    String taskId,
                                    ImmutableSet<String> inputQueueNames,
                                    ImmutableSet<String> outputQueueNames) throws ConflictException;

    /**
     * Lock for editing, returns empty if the lock is already taken
     */
    Optional<AutoCloseable> acquireLock(String organizationName, String taskId);

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = {"organizationName", "taskId"}, rangePrefix = "lambdaRecord")
    @DynamoTable(type = Gsi, indexNumber = 1, partitionKeys = "organizationName", shardKeys = "taskId", shardCount = 10, rangePrefix = "lambdaRecordByOrganizationName", rangeKeys = "taskId")
    @RegisterForReflection
    class LambdaRecord implements Node {

        @NonNull
        String organizationName;

        @NonNull
        String taskId;

        @NonNull
        String username;

        @NonNull
        ImmutableSet<String> inputQueueNames;

        @NonNull
        ImmutableSet<String> outputQueueNames;

        String endpointUrl;

        @NonNull
        Boolean isDeleted;

        Long ttlInEpochSec;

        public Optional<String> getEndpointUrlOpt() {
            return Optional.ofNullable(Strings.emptyToNull(endpointUrl));
        }

        @Override
        public String getName() {
            return taskId;
        }

        @Override
        public Set<String> getNodeInputs() {
            return inputQueueNames;
        }

        @Override
        public Set<String> getNodeOutputs() {
            return outputQueueNames;
        }
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = {"organizationName", "taskId"}, rangePrefix = "lambdaMutex")
    @RegisterForReflection
    class LambdaMutex {

        @NonNull
        String organizationName;

        @NonNull
        String taskId;

        @NonNull
        String reservationId;

        @NonNull
        Long ttlInEpochSec;
    }
}
