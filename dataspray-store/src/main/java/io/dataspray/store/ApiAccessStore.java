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
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

public interface ApiAccessStore {

    String TRIAL_USAGE_PLAN_NAME = "trial-usage-plan";
    long TRIAL_USAGE_PLAN_VERSION = 1;

    ApiAccess createApiAccess(
            String organizationName,
            UsageKeyType usageKeyType,
            String description,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt);

    ImmutableSet<ApiAccess> getApiAccessesByOrganizationName(String organizationName);

    Optional<ApiAccess> getApiAccessByApiKey(String apiKey, boolean useCache);

    void revokeApiKey(String apiKey);

    UsageKey getOrCreateUsageKeyForOrganization(String organizationName);

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

//        TODO add differentiation on who owns this api key:
//         1/ processor and version
//         2/ user of an organization
        TODO

        @NonNull
        Long usageKeyTypeId;

        @NonNull
        String description;

        @NonNull
        ImmutableSet<String> queueWhitelist;

        @Nullable
        Long ttlInEpochSec;

        public UsageKeyType getUsageKeyType() {
            return UsageKeyType.BY_ID.get(usageKeyTypeId);
        }

        /**
         * @return The usage key which is used to identify the usage of the API key.
         */
        public Optional<String> getUsageKey() {
            switch (getUsageKeyType()) {
                case UNLIMITED:
                    return Optional.empty();
                case ORGANIZATION_WIDE:
                    // Share usage key across all API keys on the same account.
                    // Let's just use the account ID as usage key since it's not a secret.
                    return Optional.of(getUsageKeyType().getId() + "-" + organizationName);
                default:
                    throw new IllegalStateException("Unknown usage key type: " + getUsageKeyType());
            }
        }

        public boolean isTtlNotExpired() {
            return ttlInEpochSec == null
                   || ttlInEpochSec >= Instant.now().getEpochSecond();
        }
    }

    /**
     * Mapping of Account ID to Api Key Amazon ID.
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

        /**
         * Unique Amazon ID for a given api key.
         * This is because ApiGateway API has no methods to fetch by api key, only by its auto-generated ID.
         */
        @NonNull
        String usageKeyId;
    }

    @Getter
    enum UsageKeyType {
        UNLIMITED(0),
        ORGANIZATION_WIDE(1);
        private final long id;

        UsageKeyType(long id) {
            this.id = id;
        }

        public static final ImmutableMap<Long, UsageKeyType> BY_ID = Arrays.stream(values())
                .collect(ImmutableMap.toImmutableMap(UsageKeyType::getId, Function.identity()));
    }
}
