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

public interface ApiAccessStore {

    String TRIAL_USAGE_PLAN_NAME = "trial-usage-plan";
    long TRIAL_USAGE_PLAN_VERSION = 1;

    ApiAccess createApiAccessForUser(
            String organizationName,
            String userEmail,
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
            String userEmail,
            String taskId,
            String taskVersion,
            UsageKeyType usageKeyType,
            Optional<ImmutableSet<String>> queueWhitelistOpt);

    ImmutableSet<ApiAccess> getApiAccessesByOrganizationName(String organizationName);

    Optional<ApiAccess> getApiAccessByApiKey(String apiKey, boolean useCache);

    void revokeApiKey(String apiKey);

    void revokeApiKeysForTaskId(String organizationName, String taskId);

    void revokeApiKeyForTaskVersion(String organizationName, String taskId, String taskVersion);

    UsageKey getOrCreateUsageKeyForOrganization(String organizationName);

    void getAllUsageKeys(Consumer<ImmutableList<UsageKey>> batchConsumer);

    Optional<String> getUsageKey(UsageKeyType type, Optional<String> userEmailOpt, ImmutableSet<String> organizationNames);

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

        /** For ownerType=USER shows user email. For ownerType=TASK, shows email of user that created task. */
        @NonNull
        String ownerEmail;

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
                case USER -> ownerEmail;
                case TASK -> checkNotNull(ownerTaskId);
            };
        }
    }

    /**
     * Mapping of organization name to Api Key Amazon ID.
     * <br />
     * An api key has its own Amazon ID that is required for all API calls, hence this mapping.
     */
    @Value
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "organizationName", rangePrefix = "usageKeyByOrganizationName")
    @DynamoTable(type = Gsi, indexNumber = 1, shardKeys = {"organizationName"}, shardCount = 10, rangePrefix = "usageKeys", rangeKeys = "organizationName")
    @RegisterForReflection
    class UsageKey {
        @NonNull
        String organizationName;

        // TODO:
        // - We want to store not only organization, but also global api keys, make this a mapping between usageKeyValue and usageKeyId
        // - Fix up naming convention of usagePlan, usageKey and apiKey.
        TODO

        /**
         * Unique Amazon ID for a given api key.
         * This is because ApiGateway API has no methods to fetch by api key, only by its auto-generated ID.
         */
        @NonNull
        String usageKeyId;
    }

    enum OwnerType {
        /** API Key used by user scripts or workflows */
        USER,
        /** API Key used by a task */
        TASK
    }

    @Getter
    @AllArgsConstructor
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
