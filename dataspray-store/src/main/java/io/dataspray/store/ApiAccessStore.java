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


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.DynamoTable;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

/**
 * Access management by API Keys.
 * <p>
 * Api keys can be used created by users with custom scope. Api keys are also used by tasks specific to a particular
 * published function version that is scoped to the outputs it produces
 * <p>
 * Terminology:
 * <ul>
 *     <li>Api Key - a unique string that is used to identify a user or task</li>
 *     <li>Usage Key - API Gateway's concept of "API key" that contains value of Api Key as well as a Usage Key ID</li>
 *     <li>Usage Key ID - API Gateway's "API key" identifier. Fetching a Usage Key can only be done by identifier, hence we need to store a mapping of api key to usage key id in dynamo</li>
 * </ul>
 */
public interface ApiAccessStore {

    String TRIAL_USAGE_PLAN_NAME = "trial-usage-plan";
    long TRIAL_USAGE_PLAN_VERSION = 1;

    ApiAccess createApiAccessForUser(
            String organizationName,
            String username,
            UsageKeyType usageKeyType,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt);

    /**
     * Returns an Api Key value without persisting any changes to database. Use
     * {@link ApiAccessStore#createApiAccessForTask} to persist the Api Access.
     * <br />
     * For tasks, the value needs to be embedded in the function, and only after the function is published,
     * we are able to see the task version and persist the Api Key.
     */
    String generateApiKey();

    ApiAccess createApiAccessForTask(
            String apiKey,
            String organizationName,
            String username,
            String taskId,
            String taskVersion,
            UsageKeyType usageKeyType,
            Optional<ImmutableSet<String>> queueWhitelistOpt);

    ImmutableSet<ApiAccess> getApiAccessesByOrganizationName(String organizationName);

    Optional<ApiAccess> getApiAccessByApiKey(String apiKey, boolean useCache);

    void revokeApiKey(String apiKey);

    void revokeApiKeysForTaskId(String organizationName, String taskId);

    void revokeApiKeyForTaskVersion(String organizationName, String taskId, String taskVersion);

    /**
     * Retrieve the Usage Key if the given access requires a Usage Key.
     */
    Optional<UsageKey> getUsageKey(UsageKeyType type, Optional<String> usernameOpt, ImmutableSet<String> organizationNames);

    void getAllUsageKeys(Consumer<ImmutableList<UsageKey>> batchConsumer);

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "apiKey", rangePrefix = "apiKey")
    @DynamoTable(type = Gsi, indexNumber = 1, partitionKeys = "organizationName", rangePrefix = "apiKeyByOrganizationName")
    @RegisterForReflection
    class ApiAccess {
        @NonNull
        @EqualsAndHashCode.Include
        @ToString.Exclude
        String apiKey;

        @NonNull
        String organizationName;

        @NonNull
        OwnerType ownerType;

        /** For ownerType=USER shows user's username. For ownerType=TASK, shows username of user that deployed the task. */
        @NonNull
        String ownerUsername;

        /** For ownerType=TASK, the task ID */
        String ownerTaskId;

        /** For ownerType=TASK, the task version. */
        String ownerTaskVersion;

        @NonNull
        UsageKeyType usageKeyType;

        @NonNull
        ImmutableSet<String> queueWhitelist;

        @Nullable
        Long ttlInEpochSec;

        public boolean isTtlNotExpired() {
            return ttlInEpochSec == null
                   || ttlInEpochSec >= Instant.now().getEpochSecond();
        }

        public String getPrincipalId() {
            return switch (ownerType) {
                case USER -> ownerUsername;
                case TASK -> checkNotNull(ownerTaskId);
            };
        }
    }

    /**
     * Mapping of Usage Key's API Key name to Usage Key's generated ID.
     * <br />
     * An api key has its own Amazon ID that is required for all API calls, hence this mapping.
     */
    @Value
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "apiKey", rangePrefix = "usageKeyByApiKey")
    @DynamoTable(type = Gsi, indexNumber = 1, shardKeys = "apiKey", shardCount = 10, rangePrefix = "allUsageKeys", rangeKeys = "apiKey")
    @RegisterForReflection
    class UsageKey {

        /**
         * Not to be confused with DataSpray's Api Key, this is the AWS API Gateway's Usage Key's API Key.
         * <br />
         * Typically a deterministic api key constructed from relevant parts such as organization name.
         * <br />
         * See {@link ApiAccessStore#getUsageKey} for the format of this Api Key.
         */
        @NonNull
        String apiKey;

        /**
         * Unique Amazon ID for a given api key.
         * This is because ApiGateway API has no methods to fetch by api key, only by its auto-generated ID.
         */
        @NonNull
        String usageKeyId;
    }

    @RegisterForReflection
    enum OwnerType {
        /** API Key used by user scripts or workflows */
        USER,
        /** API Key used by a task */
        TASK
    }

    @Getter
    @AllArgsConstructor
    @RegisterForReflection
    enum UsageKeyType {
        /** No Usage Key */
        UNLIMITED(0),
        /** Usage Key shared for entire organization */
        ORGANIZATION(1),
        /** Usage Key shared globally */
        GLOBAL(2);
        private final long id;
    }
}
