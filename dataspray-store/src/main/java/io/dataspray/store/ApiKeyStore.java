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


import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.DynamoTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.time.Instant;
import java.util.Optional;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

public interface ApiKeyStore {

    ApiKey createApiKey(
            String accountId,
            String usagePlanId,
            String description,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt);

    ImmutableSet<ApiKey> getApiKeysByAccountId(String accountId);

    String getApiKeyValueById(String apiKeyId);

    Optional<ApiKey> getApiKey(String apiKeyValue, boolean useCache);

    /** Warning: causes API key to temporarily return 403 during operation */
    void switchUsagePlanId(String apiKeyValue, String usagePlanId);

    void revokeApiKey(String apiKeyValue);

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "apiKeyValue", rangePrefix = "apiKey")
    @DynamoTable(type = Gsi, indexNumber = 1, partitionKeys = {"accountId"}, rangePrefix = "apiKeyByAccountId")
    class ApiKey {
        @NonNull
        @EqualsAndHashCode.Include
        @ToString.Exclude
        String apiKeyValue;

        @NonNull
        String accountId;

        @NonNull
        String apiKeyId;

        @NonNull
        String description;

        @NonNull
        ImmutableSet<String> queueWhitelist;

        Long ttlInEpochSec;
    }
}
