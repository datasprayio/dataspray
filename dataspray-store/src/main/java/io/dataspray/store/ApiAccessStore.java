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


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.DynamoTable;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

public interface ApiAccessStore {

    String TRIAL_USAGE_PLAN_NAME = "trial-usage-plan";
    long TRIAL_USAGE_PLAN_VERSION = 1;

    ApiAccess createApiAccess(
            String accountId,
            UsageKeyType usageKeyType,
            String description,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt);

    ImmutableSet<ApiAccess> getApiAccessesByAccountId(String accountId);

    Optional<ApiAccess> getApiAccessByApiKey(String apiKey, boolean useCache);

    void revokeApiKey(String apiKeyValue);

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "apiKey", rangePrefix = "apiKey")
    @DynamoTable(type = Gsi, indexNumber = 1, partitionKeys = "accountId", rangePrefix = "apiKeyByAccountId")
    @RegisterForReflection
    class ApiAccess {
        @NonNull
        @EqualsAndHashCode.Include
        @ToString.Exclude
        String apiKey;

        @NonNull
        String accountId;

        @NonNull
        Long usageKeyTypeId;

        @NonNull
        String description;

        @NonNull
        ImmutableSet<String> queueWhitelist;

        Long ttlInEpochSec;

        public UsageKeyType getUsageKeyType() {
            return UsageKeyType.BY_ID.get(usageKeyTypeId);
        }

        public Optional<String> getUsageKey() {
            switch (getUsageKeyType()) {
                case UNLIMITED:
                    return Optional.empty();
                case ACCOUNT_WIDE:
                    return Optional.of(accountId);
                default:
                    throw new IllegalStateException("Unknown usage key type: " + getUsageKeyType());
            }
        }
    }

    enum UsageKeyType {
        UNLIMITED(0),
        ACCOUNT_WIDE(1);
        private final long id;

        UsageKeyType(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

        public static final ImmutableMap<Long, UsageKeyType> BY_ID = Arrays.stream(values())
                .collect(ImmutableMap.toImmutableMap(UsageKeyType::getId, Function.identity()));
    }
}
